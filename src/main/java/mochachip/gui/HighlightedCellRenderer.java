package mochachip.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

//Custom table cell renderer to highlight the next instruction to be executed
public class HighlightedCellRenderer extends DefaultTableCellRenderer {
    private DebugGUI debugGUI;

    public HighlightedCellRenderer(DebugGUI debugGUI) {
        this.debugGUI = debugGUI;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component cellComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (debugGUI != null && debugGUI.getCurrentInstruction() != null) {
            int currentInstructionAddress = debugGUI.getCurrentInstruction().getAddress();
            int cellAddress = Integer.parseInt((String) table.getValueAt(row, 1), 16);
            ///If currentInstruction's address is equal to this cell's address column value, highlight it yellow
            if (cellAddress == currentInstructionAddress && column != 0) {
                cellComponent.setBackground(new Color(255,255,0,75));
            } else {
                cellComponent.setBackground(debugGUI.bgColor);
            }
        }
        return cellComponent;
    }
}
