package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.rules.Rule;
import com.ourgiant.docscrubber.rules.RuleSet;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class RulesTableModel extends AbstractTableModel {

    private static final String[] COLUMNS =
        {"Enabled", "ID", "Name", "Family", "Type", "Severity", "Weight", "Channels", "Tags"};

    private List<Rule> rules = List.of();
    private RuleSet ruleSet;

    void setRules(List<Rule> rules, RuleSet ruleSet) {
        this.rules = List.copyOf(rules);
        this.ruleSet = ruleSet;
        fireTableDataChanged();
    }

    Rule ruleAt(int row) {
        return rules.get(row);
    }

    @Override
    public int getRowCount() {
        return rules.size();
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
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? Boolean.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Rule r = rules.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> r.isEnabled();
            case 1 -> r.getId();
            case 2 -> r.getName();
            case 3 -> r.getFamily() == null ? "" : r.getFamily().json();
            case 4 -> r.getType() == null ? "" : r.getType().json();
            case 5 -> r.getSeverity() == null ? "" : r.getSeverity().json();
            case 6 -> ruleSet == null ? "" : String.valueOf(ruleSet.weightFor(r));
            case 7 -> r.appliesToAllChannels() ? "*" : String.join(", ", r.getChannels());
            case 8 -> String.join(", ", r.getTags());
            default -> "";
        };
    }
}
