package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.engine.Finding;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class FindingsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Severity", "Rule", "Channel", "Location", "Evidence"};

    private List<Finding> findings = List.of();

    void setFindings(List<Finding> findings) {
        this.findings = List.copyOf(findings);
        fireTableDataChanged();
    }

    Finding findingAt(int row) {
        return findings.get(row);
    }

    @Override
    public int getRowCount() {
        return findings.size();
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
        Finding f = findings.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> f.getSeverity().json();
            case 1 -> f.getRuleName() + " (" + f.getRuleId() + ")";
            case 2 -> f.getChannel().name();
            case 3 -> f.getLocation().describe();
            case 4 -> f.getEvidence();
            default -> "";
        };
    }
}
