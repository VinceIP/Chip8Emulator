import java.util.concurrent.ThreadLocalRandom;

public class CPU {
    //handles all CPU operations
    Memory memory;
    ProgramCounter programCounter;
    Registers registers;
    Timer timer;
    Input input;
    Stack stack;
    Display display;
    boolean waitingForKeyPress = false;
    int waitingRegister;

    public CPU(Display display) {
        this.memory = new Memory();
        this.stack = new Stack();
        this.programCounter = new ProgramCounter();
        this.registers = new Registers();
        this.timer = new Timer();
        this.input = new Input(this);
        this.display = display;
        display.input = this.input;
    }

    public void start() {
        memory.initialize();
        memory.loadChip8File();
        long delay = 1; //Delay in ms - roughly 60hz
        while (true) {
            registers.update();
            if (!waitingForKeyPress) cycle();
            else {
                if (input.isAnyKeyPressed()) {
                    registers.variableRegisters[waitingRegister] = (byte) input.getLastKeyPressed();
                    input.resetLastKeyPressed();
                    waitingForKeyPress = false;
                    waitingRegister = -1;
                }
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void cycle() {
        int currentInstruction = fetchInstruction();
        decode(currentInstruction);
    }

    //Fetch a 2-byte instruction at address in memory
    public int fetchInstruction() {
        if (programCounter.getCurrentAddress() < 0 || programCounter.getCurrentAddress() >= 4096) {
            throw new IllegalArgumentException("ERROR: Program counter out of bounds.");
        }
        //Unsign and parse instructions to Strings, combine them, parse to ints to avoid annoying bitwise operations
        //Yeah, yeah, i know
        String byte1 = String.format("%02X", memory.read(programCounter.getCurrentAddress()) & 0xFF);
        String byte2 = String.format("%02X", memory.read(programCounter.getCurrentAddress() + 1) & 0xFF);
        int instruction = Integer.parseInt(byte1 + byte2, 16);

        programCounter.incrementPC();
        return instruction;
    }

    public void decode(int instruction) {
        //We're going to avoid bitwise operations entirely and use strings
        //to analyze hex values for decoding
        String instrStr = String.format("%04X", instruction);
        String opCode = instrStr.substring(0, 1); //First char indicates the opcode
        String xStr = instrStr.substring(1, 2);
        String yStr = instrStr.substring(2, 3);
        String nStr = instrStr.substring(3, 4);
        String nnStr = instrStr.substring(2);
        String nnnStr = instrStr.substring(1);

        //Parse strings into their values in base 16
        int x = Integer.parseInt(xStr, 16);
        int y = Integer.parseInt(yStr, 16);
        byte n = (byte) (Integer.parseInt(nStr, 16) & 0xFF); //4 bits, cast to unsigned byte
        byte nn = (byte) (Integer.parseInt(nnStr, 16) & 0xFF); //8 bits, cast to unsigned byte
        int nnn = Integer.parseInt(nnnStr, 16); ///nnn is probably 12 bits, so an int is needed

        //System.out.println("Decoding instruction: " + String.format("%04X", instruction) + " at PC: " + String.format("%04X", programCounter.getCurrentAddress()));

        switch (opCode) {

//            nnn or addr - A 12-bit value, the lowest 12 bits of the instruction
//            n or nibble - A 4-bit value, the lowest 4 bits of the instruction
//            x - A 4-bit value, the lower 4 bits of the high byte of the instruction
//            y - A 4-bit value, the upper 4 bits of the low byte of the instruction
//            kk or byte - An 8-bit value, the lowest 8 bits of the instruction

            case "0":
                //00e0 CLS -  clear screen
                if (xStr.equals("0") && yStr.equals("E") && nStr.equals("0")) {
                    cls();
                }

                //00ee RET - return
                else if (xStr.equals("0") && yStr.equals("E") && nStr.equals("E")) {
                    ret();
                }
                break;

            //1nnn JP addr - jump
            case "1":
                //System.out.println("Jumping to address: " + String.format("%04X", nnn));
                jp(nnn);
                break;

            //2nnn CALL addr - call subroutine
            case "2":
                call(nnn);
                break;

            //3xnn SE Vx, byte - Skip next instruction if Vx = nn
            case "3":
                seCompareByte(x, nn);
                break;

            //4xnn SNE Vx, byte - Skip next instruction if Vx != nn
            case "4":
                sne(x, nn);
                break;

            //5xy0 SE Vx, Vy - Skip next instruction if Vx = Vy
            case "5":
                seCompareRegister(x, y);
                break;

            //6xnn LD Vx, byte - Puts value of nn into Vx
            case "6":
                ldByte(x, nn);
                break;

            //7xnn ADD Vx, byte - Set Vx = Vx + nn
            case "7":
                addByte(x, nn);
                break;

            case "8":
                //8xy0 LD Vx, Vy - Set Vx = Vy
                if (nStr.equals("0")) {
                    ldRegister(x, y);

                    // 8xy1 OR Vx, Vy - Set Vx = Vx OR Vy
                } else if (nStr.equals("1")) {
                    logicalOR(x, y);

                    //8xy2 AND Vx, Vy - Set Vx = Vx & Vy
                } else if (nStr.equals("2")) {
                    logicalAND(x, y);

                    // 8xy3 XOR Vx, Vy - Set Vx = Vx ^ Vy
                } else if (nStr.equals("3")) {
                    logicalXOR(x, y);

                    //8xy4 ADD Vx, Vy - Set Vx - Vx + Vy, set VF carry
                } else if (nStr.equals("4")) {
                    addWithCarry(x, y);

                    // 8xy5 SUB Vx, Vy - Set Vx = Vx - Vy
                } else if (nStr.equals("5")) {
                    subWithCarry(x, y);

                    //8xy6 SHR Vx {, Vy} - Set Vx = Vx SHR 1 (shift right)
                    // If the least-significant bit of Vx is 1, then VF is set to 1, otherwise 0. Then Vx is divided by 2.
                } else if (nStr.equals("6")) {
                    bitshiftRight(x);

                    // 8xy7 SUBN Vx, Vy - Set Vx = Vy - Vx
                    // If Vy > Vx, then VF is set to 1, otherwise 0. Then Vx is subtracted from Vy, and the results stored in Vx.
                } else if (nStr.equals("7")) {
                    subWithCarryReverse(y, x);

                    //8xyE SHL Vx {, Vy}
                    //Set Vx = Vx SHL 1.
                    //If the most-significant bit of Vx is 1, then VF is set to 1, otherwise to 0. Then Vx is multiplied by 2.
                } else if (nStr.equals("E")) {
                    bitshiftLeft(x);
                }
                break;

            //9xy0 SNE Vx, Vy - Skip next  instruction if Vx != Vy
            case "9":
                sneRegister(x, y);
                break;

            // Annn LD I, addr - Set I to nnn
            case "A":
                ldI(nnn);
                break;

            // Bnnn JP V0, addr - Jump to location nnn + V0
            case "B":
                jpTo(nnn);
                break;

            // Cxnn RND Vx, byte - Set Vx = random byte AND nn
            case "C":
                rnd(x, nn);
                break;

            //Dxyn  DRW Vx, Vy, nibble
            //Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision.

            //The interpreter reads n bytes from memory, starting at the address stored in I. These bytes are then displayed
            // as sprites on screen at coordinates (Vx, Vy). Sprites are XORed onto the existing screen. If this causes any
            // pixels to be erased, VF is set to 1, otherwise it is set to 0. If the sprite is positioned so part of it is outside the
            // coordinates of the display, it wraps around to the opposite side of the screen. See instruction 8xy3 for more information
            // on XOR, and section 2.4, Display, for more information on the Chip-8 screen and sprites.
            case "D":
                draw(x, y, n);
                break;


            case "E":
                //Ex9E  SKP Vx
                //Skip next instruction if key with the value of Vx is pressed.
                //Checks the keyboard, and if the key corresponding to the value of Vx is currently in the down position, PC is increased by 2.
                if (nStr.equals("E")) {
                    skp(x);

                    //ExA1 - SKNP Vx
                    //Skip next instruction if key with the value of Vx is not pressed.
                    //Checks the keyboard, and if the key corresponding to the value of Vx is currently in the up position, PC is increased by 2.
                } else if (nnStr.equals("A1")) {
                    sknp(x);
                }
                break;


            case "F":
                // Fx07 LD Vx, DT - Set Vx = delay timer value
                if (nnStr.equals("07")) {
                    ldDelayTimer(x);

                    //Fx0A  LD Vx, K
                    //Wait for a key press, store the value of the key in Vx.
                    //All execution stops until a key is pressed, then the value of that key is stored in Vx.
                } else if (nnStr.equals("0A")) {
                    ldKey(x);

                    // Fx15 LD DT, Vx - Set delay timer = Vx
                } else if (nnStr.equals("15")) {
                    ldDelayTimerFromRegister(x);

                    // Fx18 LD ST, Vx - Set sound timer = Vx
                } else if (nnStr.equals("18")) {
                    ldSoundTimer(x);

                    // Fx1E ADD I, Vx - Set I = I + Vx
                } else if (nnStr.equals("1E")) {
                    addI(x);

                    // Fx29 LD F, Vx - Set I = location of sprite digit Vx
                } else if (nnStr.equals("29")) {
                    ldFontDigit(x);

                    //Fx33 LD B, Vx
                    //Store binary-coded decimal representation of Vx in memory locations I, I+1, and I+2.
                    //The interpreter takes the decimal value of Vx, and places the hundreds digit in memory at location in I, the tens digit at location I+1, and the ones digit at location I+2.
                } else if (nnStr.equals("33")) {
                    ldBCD(x);

                    //Fx55 LD [I], Vx
                    //Store registers V0 through Vx in memory starting at location I.
                    //The interpreter copies the values of registers V0 through Vx into memory, starting at the address in I.
                } else if (nnStr.equals("55")) {
                    ldIFor(x);

                    //Fx65 - LD Vx, [I]
                    //Read registers V0 through Vx from memory starting at location I.
                    //The interpreter reads values from memory starting at location I into registers V0 through Vx.
                } else if (nnStr.equals("65")) {
                    ldIForRead(x);
                }
                break;
            default:
                throw new OpcodeUnimplementedException(instruction);


        }
    }

    private void ret() {
        int returnAddress = stack.pop();
        programCounter.jump(returnAddress);
    }

    private void call(int address) {
        stack.push(programCounter.currentAddress);
        programCounter.jump(address);
    }

    //Draw sprite at x, y, with height n
    private void draw(int x, int y, int n) {
        //System.out.println("Draw sprite at V" + x + ", V" + y + " at height " + n);
        int vx = (registers.variableRegisters[x] & 0xFF) % 64;
        int vy = (registers.variableRegisters[y] & 0xFF) % 64;
        registers.variableRegisters[0xF] = 0;
        //for n rows
        for (int i = 0; i < n; i++) {
            int spriteData = memory.read(registers.indexRegister + i);
            //for each 8 pixels/bits in a row
            for (int j = 0; j < 8; j++) {
                //is this bit set?
                if ((spriteData & (0x80 >> j)) != 0) {
                    int displayX = (vx + j) % 64;
                    int displayY = (vy + i) % 32;

                    if (display.getPixelState(displayX, displayY)) {
                        registers.variableRegisters[0xF] = 1; //Set VF to 1 if collision
                        display.setPixel(displayX, displayY, false);
                    } else {
                        display.setPixel(displayX, displayY, true);
                    }
                }
            }

        }
        display.updateDisplay();

    }

    private void cls() {
        display.clearScreen();
    }

    private void jp(int nnn) {
        programCounter.jump(nnn);
    }

    private void jpTo(int nnn) {
        programCounter.jump(nnn + registers.variableRegisters[0x0]);
    }

    private void seCompareByte(int x, byte nn) {
        if (registers.variableRegisters[x] == nn) programCounter.incrementPC();
    }

    private void seCompareRegister(int x, int y) {
        if (registers.variableRegisters[x] == registers.variableRegisters[y]) {
            programCounter.incrementPC();
        }
    }

    private void sne(int x, byte nn) {
        if (registers.variableRegisters[x] != nn) programCounter.incrementPC();
    }

    private void sneRegister(int x, int y) {
        if (registers.variableRegisters[x] != registers.variableRegisters[y]) programCounter.incrementPC();
    }

    private void addByte(int x, byte nn) {
        registers.variableRegisters[x] += (byte) (nn & 0xFF);
    }

    private void addWithCarry(int x, int y) {
        byte result = (byte) (registers.variableRegisters[x] + registers.variableRegisters[y]);
        int iResult = (result & 0xFF);
        int vx = registers.variableRegisters[x] & 0xFF;
        int vy = registers.variableRegisters[y] & 0xFF;
        if (iResult < vx || iResult < vy) {
            registers.variableRegisters[0xF] = 0x1;
        } else {
            registers.variableRegisters[0xF] = 0x0;
        }
        registers.variableRegisters[x] = result;
    }

    private void addI(int x) {
        registers.indexRegister += registers.variableRegisters[x];

    }

    private void subWithCarry(int x, int y) {
        //If this calculation will underflow, set carry flag - clear it if not
        registers.variableRegisters[0xF] = (byte) (registers.variableRegisters[x] > registers.variableRegisters[y] ? 1 : 0);
        registers.variableRegisters[x] = (byte) ((registers.variableRegisters[x] - registers.variableRegisters[y]) & 0xFF);
    }

    private void subWithCarryReverse(int y, int x) {
        int vx = registers.variableRegisters[x] & 0xFF;
        int vy = registers.variableRegisters[y] & 0xFF;
        registers.variableRegisters[0xF] = (byte) (vy > vx ? 1 : 0); // Set VF to 1 if no borrow
        int difference = vy - vx;
        registers.variableRegisters[x] = (byte) (difference & 0xFF); // Ensure result is within 8 bit
    }

    private void ldByte(int x, byte nn) {
        registers.variableRegisters[x] = (byte) (nn & 0xFF);
    }

    private void ldRegister(int x, int y) {
        registers.variableRegisters[x] = registers.variableRegisters[y];
    }

    private void ldI(int nnn) {
        registers.indexRegister = nnn;
    }

    private void ldIFor(int x) {
        if (x == 0) memory.write(registers.indexRegister, registers.variableRegisters[0]);
        else {
            for (int i = 0; i <= x; i++) {
                memory.write(registers.indexRegister + i, registers.variableRegisters[i]);
            }
        }
    }

    private void ldIForRead(int x) {
        if (x == 0) registers.variableRegisters[0] = memory.read(registers.indexRegister);
        else {
            for (int i = 0; i <= x; i++) {
                registers.variableRegisters[i] = memory.read(registers.indexRegister + i);
            }
        }
    }

    private void ldDelayTimer(int x) {
        registers.variableRegisters[x] = registers.delayTimer;
    }

    private void ldDelayTimerFromRegister(int x) {
        registers.delayTimer = registers.variableRegisters[x];
    }

    private void ldSoundTimer(int x) {
        registers.soundTimer = registers.variableRegisters[x];
    }

    private void ldKey(int x) {
        waitingForKeyPress = true;
        waitingRegister = x;
    }

    private void ldFontDigit(int x) {
        registers.indexRegister = memory.getAddressOfDigit(registers.variableRegisters[x]);
    }

    private void ldBCD(int x) {
        int dec = registers.variableRegisters[x] & 0xFF;
        int hundreds = dec / 100;
        int tens = (dec / 10) % 10;
        int ones = dec % 10;
        System.out.printf("Storing BCD of %d: [%d, %d, %d] at addresses I=%04X, I+1=%04X, I+2=%04X%n",
                dec, hundreds, tens, ones, registers.indexRegister, registers.indexRegister + 1, registers.indexRegister + 2);
        memory.write(registers.indexRegister, (byte) hundreds); // Hundreds place
        memory.write(registers.indexRegister + 1, (byte) tens); // Tens place
        memory.write(registers.indexRegister + 2, (byte) ones); // Ones place
    }

    private void logicalOR(int x, int y) {
        registers.variableRegisters[x] = (byte) (registers.variableRegisters[x] | registers.variableRegisters[y]);

    }

    private void logicalAND(int x, int y) {
        registers.variableRegisters[x] = (byte) (registers.variableRegisters[x] & registers.variableRegisters[y]);
    }

    private void logicalXOR(int x, int y) {
        registers.variableRegisters[x] = (byte) (registers.variableRegisters[x] ^ registers.variableRegisters[y]);

    }

    private void bitshiftRight(int x) {
        // Store the least significant bit in carry flag (VF)
        registers.variableRegisters[0xF] = (byte) (registers.variableRegisters[x] & 0x01);
        // Shift Vx right by one bit
        registers.variableRegisters[x] = (byte) ((registers.variableRegisters[x] & 0xFF) >> 1);
    }

    private void bitshiftLeft(int x) {
        // Store the most significant bit in carry flag (VF)
        registers.variableRegisters[0xF] = (byte) ((registers.variableRegisters[x] & 0x80) >> 7);
        // Shift Vx left by one bit and mask to 8 bits
        registers.variableRegisters[x] = (byte) ((registers.variableRegisters[x] << 1) & 0xFF);
    }

    private void rnd(int x, int nn) {
        registers.variableRegisters[x] = (byte) ((byte) ((byte) ThreadLocalRandom.current().nextInt(0, 255) & 0xFF) & nn);
    }

    private void skp(int x) {
        if (input.isKeyPressed(registers.variableRegisters[x])) {
            programCounter.incrementPC();
        }
    }

    private void sknp(int x) {
        if (!input.isKeyPressed(registers.variableRegisters[x])) {
            programCounter.incrementPC();
        }
    }

}

class OpcodeUnimplementedException extends RuntimeException {
    public OpcodeUnimplementedException(int instruction) {
        System.out.println("ERROR: Instruction " + instruction + " unimplemented.");
    }
}
