package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.rules.Combo;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class CombosTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"ID", "Multiplier", "Same Fragment", "Require Tags", "Description"};

    private List<Combo> combos = List.of();

    void setCombos(List<Combo> combos) {
        this.combos = List.copyOf(combos);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return combos.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Combo c = combos.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> c.getId();
            case 1 -> String.format("%.2fx", c.getMultiplier());
            case 2 -> c.isSameFragment() ? "Yes" : "No";
            case 3 -> String.join(", ", c.getRequireTags());
            case 4 -> c.getDescription();
            default -> "";
        };
    }
}
