package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.rules.Rule;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.RulesValidator;
import com.ourgiant.docscrubber.rules.RulesWriter;
import com.ourgiant.docscrubber.rules.ValidationResult;
import com.ourgiant.docscrubber.rules.VerdictThresholds;
import com.ourgiant.docscrubber.rules.detector.DetectorRegistry;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Inspector and editor for the currently loaded ruleset — what a "poison pill" rule actually checks
 * for, in plain view, since the whole rules engine is meant to be inspectable rather than opaque.
 * Non-modal so it can stay open while scanning, and {@link #refresh} is called by {@code MainWindow}
 * whenever rules are (re)loaded so it never shows a stale ruleset without the user asking.
 *
 * <p>Edits (add/edit/duplicate/delete) apply only to this window's in-memory working copy — they
 * never touch the app's actively-scanning ruleset until {@link #onSave}/{@link #onSaveAs} writes
 * them to disk, which hands the path to {@code onSaved} so {@code MainWindow} reloads it through
 * the exact same validated path as "Load Rules File..." (proving the write round-trips, not just
 * trusting it did).
 */
final class RulesWindow extends JDialog {

    private static final String ALL_FAMILIES = "All Families";
    private static final String ALL_SEVERITIES = "All Severities";

    private final DetectorRegistry detectorRegistry;
    private final RulesValidator validator;
    private final Consumer<Path> onSaved;

    private final JLabel sourceLabel = new JLabel(" ");
    private final JLabel weightsLabel = new JLabel(" ");
    private final JLabel thresholdsLabel = new JLabel(" ");

    private final JTextField searchField = new JTextField(18);
    private final JComboBox<String> familyCombo = new JComboBox<>(new String[]{ALL_FAMILIES, "Content", "Structural"});
    private final JComboBox<String> severityCombo =
        new JComboBox<>(new String[]{ALL_SEVERITIES, "Info", "Low", "Medium", "High", "Critical"});
    private final JCheckBox enabledOnlyCheck = new JCheckBox("Enabled only");

    private final JButton addRuleButton = new JButton("Add Rule...");
    private final JButton editRuleButton = new JButton("Edit Rule...");
    private final JButton duplicateRuleButton = new JButton("Duplicate Rule...");
    private final JButton deleteRuleButton = new JButton("Delete Rule");
    private final JButton saveButton = new JButton("Save");
    private final JButton saveAsButton = new JButton("Save As...");
    private final JLabel dirtyLabel = new JLabel(" ");

    private final RulesTableModel rulesTableModel = new RulesTableModel();
    private final JTable rulesTable = new JTable(rulesTableModel);
    private final JTextArea detailArea = new JTextArea();

    private final CombosTableModel combosTableModel = new CombosTableModel();
    private final JTable combosTable = new JTable(combosTableModel);

    private final JTabbedPane tabs = new JTabbedPane();

    private RuleSet currentRuleSet;
    private Path currentRulesPath;
    private boolean dirty;

    RulesWindow(Frame parent, DetectorRegistry detectorRegistry, RulesValidator validator, Consumer<Path> onSaved) {
        super(parent, "Rules", false);
        this.detectorRegistry = detectorRegistry;
        this.validator = validator;
        this.onSaved = onSaved;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildHeaderPanel(), BorderLayout.NORTH);
        tabs.addTab("Rules", buildRulesTab());
        tabs.addTab("Combos", buildCombosTab());
        add(tabs, BorderLayout.CENTER);

        setSize(960, 660);
        setLocationRelativeTo(parent);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        Font small = sourceLabel.getFont().deriveFont(Font.PLAIN, 12f);
        sourceLabel.setFont(small.deriveFont(Font.BOLD));
        weightsLabel.setFont(small);
        thresholdsLabel.setFont(small);
        panel.add(sourceLabel);
        panel.add(weightsLabel);
        panel.add(thresholdsLabel);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(0, 0, 8, 0)));
        return panel;
    }

    private JPanel buildRulesTab() {
        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionsBar.add(addRuleButton);
        actionsBar.add(editRuleButton);
        actionsBar.add(duplicateRuleButton);
        actionsBar.add(deleteRuleButton);
        actionsBar.add(Box.createHorizontalStrut(16));
        actionsBar.add(saveButton);
        actionsBar.add(saveAsButton);
        dirtyLabel.setForeground(new Color(0xB0, 0x60, 0x00));
        actionsBar.add(dirtyLabel);

        addRuleButton.addActionListener(e -> onAddRule());
        editRuleButton.addActionListener(e -> onEditRule());
        duplicateRuleButton.addActionListener(e -> onDuplicateRule());
        deleteRuleButton.addActionListener(e -> onDeleteRule());
        saveButton.addActionListener(e -> onSave());
        saveAsButton.addActionListener(e -> onSaveAs());

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filterBar.add(new JLabel("Search:"));
        filterBar.add(searchField);
        filterBar.add(new JLabel("Family:"));
        filterBar.add(familyCombo);
        filterBar.add(new JLabel("Severity:"));
        filterBar.add(severityCombo);
        filterBar.add(enabledOnlyCheck);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        });
        familyCombo.addActionListener(e -> applyFilters());
        severityCombo.addActionListener(e -> applyFilters());
        enabledOnlyCheck.addActionListener(e -> applyFilters());

        rulesTable.setRowHeight(22);
        rulesTable.setAutoCreateRowSorter(true);
        rulesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedRuleDetail();
                updateActionButtonStates();
            }
        });

        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        detailArea.setText("Select a rule to see full details.");

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(rulesTable), new JScrollPane(detailArea));
        split.setResizeWeight(0.6);
        split.setContinuousLayout(true);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(actionsBar, BorderLayout.NORTH);
        northPanel.add(filterBar, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(northPanel, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCombosTab() {
        combosTable.setRowHeight(22);
        combosTable.setAutoCreateRowSorter(true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(combosTable), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Called by {@code MainWindow} on open and every time the ruleset is (re)loaded — including
     * right after this window's own {@link #onSave}/{@link #onSaveAs} writes a file, which is what
     * resets {@link #dirty} back to false once the save round-trips successfully.
     */
    void refresh(RuleSet ruleSet, Path rulesPath) {
        this.currentRuleSet = ruleSet;
        this.currentRulesPath = rulesPath;
        this.dirty = false;
        setTitle(ruleSet == null ? "Rules"
            : "Rules — " + (rulesPath == null ? "bundled default" : rulesPath.toString()));
        updateHeader(ruleSet, rulesPath);
        combosTableModel.setCombos(ruleSet == null ? List.of() : ruleSet.getCombos());
        rulesTable.clearSelection();
        detailArea.setText("Select a rule to see full details.");
        applyFilters();
        updateActionButtonStates();
    }

    private void updateActionButtonStates() {
        boolean hasSelection = rulesTable.getSelectedRow() >= 0;
        boolean hasRuleSet = currentRuleSet != null;
        addRuleButton.setEnabled(hasRuleSet);
        editRuleButton.setEnabled(hasRuleSet && hasSelection);
        duplicateRuleButton.setEnabled(hasRuleSet && hasSelection);
        deleteRuleButton.setEnabled(hasRuleSet && hasSelection);
        saveAsButton.setEnabled(hasRuleSet);
        saveButton.setEnabled(hasRuleSet && dirty && currentRulesPath != null);
        saveButton.setToolTipText(currentRulesPath == null
            ? "Save As required — currently using bundled default rules" : null);
        dirtyLabel.setText(dirty ? "Unsaved changes — Save to apply them to scans" : " ");
    }

    private Rule selectedRule() {
        int viewRow = rulesTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return rulesTableModel.ruleAt(rulesTable.convertRowIndexToModel(viewRow));
    }

    private void onAddRule() {
        if (currentRuleSet == null) {
            return;
        }
        RuleEditDialog dialog = new RuleEditDialog(this, "Add Rule", null, allRuleIds(), detectorRegistry);
        dialog.setVisible(true);
        Rule newRule = dialog.getResult();
        if (newRule == null) {
            return;
        }
        List<Rule> updated = new ArrayList<>(currentRuleSet.getRules());
        updated.add(newRule);
        applyEdit(updated, newRule.getId());
    }

    private void onEditRule() {
        Rule selected = selectedRule();
        if (selected == null || currentRuleSet == null) {
            return;
        }
        Set<String> reservedIds = allRuleIds();
        reservedIds.remove(selected.getId());
        RuleEditDialog dialog = new RuleEditDialog(this, "Edit Rule", selected, reservedIds, detectorRegistry);
        dialog.setVisible(true);
        Rule edited = dialog.getResult();
        if (edited == null) {
            return;
        }
        List<Rule> updated = new ArrayList<>(currentRuleSet.getRules());
        updated.set(updated.indexOf(selected), edited);
        applyEdit(updated, edited.getId());
    }

    private void onDuplicateRule() {
        Rule selected = selectedRule();
        if (selected == null || currentRuleSet == null) {
            return;
        }
        RuleEditDialog dialog = new RuleEditDialog(this, "Duplicate Rule", selected, allRuleIds(), detectorRegistry);
        dialog.clearIdField();
        dialog.setVisible(true);
        Rule newRule = dialog.getResult();
        if (newRule == null) {
            return;
        }
        List<Rule> updated = new ArrayList<>(currentRuleSet.getRules());
        updated.add(newRule);
        applyEdit(updated, newRule.getId());
    }

    private void onDeleteRule() {
        Rule selected = selectedRule();
        if (selected == null || currentRuleSet == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete rule " + selected.getId() + " (" + selected.getName() + ")?",
            "Confirm delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        List<Rule> updated = new ArrayList<>(currentRuleSet.getRules());
        updated.remove(selected);
        applyEdit(updated, null);
    }

    private Set<String> allRuleIds() {
        Set<String> ids = new HashSet<>();
        for (Rule rule : currentRuleSet.getRules()) {
            ids.add(rule.getId());
        }
        return ids;
    }

    /**
     * Re-validates the whole edited ruleset before accepting it — an edit is never applied silently
     * invalid — then re-selects {@code selectAfterId} (the rule that was just added/edited/duplicated,
     * or {@code null} after a delete) so the table and detail pane give immediate visual confirmation
     * of what changed instead of dropping the selection.
     */
    private void applyEdit(List<Rule> updatedRules, String selectAfterId) {
        RuleSet updated = new RuleSet(currentRuleSet.getSchemaVersion(), currentRuleSet.getSeverityWeights(),
            currentRuleSet.getVerdictThresholds(), currentRuleSet.getCombos(), updatedRules);
        ValidationResult result = validator.validate(updated);
        if (!result.isValid()) {
            ValidationDialogs.show(this, "Cannot apply change", result);
            return;
        }
        currentRuleSet = updated;
        dirty = true;
        applyFilters();
        updateActionButtonStates();
        if (selectAfterId != null) {
            selectRuleById(selectAfterId);
        } else {
            rulesTable.clearSelection();
            detailArea.setText("Select a rule to see full details.");
        }
        if (!result.getWarnings().isEmpty()) {
            ValidationDialogs.show(this, "Applied with warnings", result);
        }
    }

    private void selectRuleById(String id) {
        for (int viewRow = 0; viewRow < rulesTable.getRowCount(); viewRow++) {
            int modelRow = rulesTable.convertRowIndexToModel(viewRow);
            if (id.equals(rulesTableModel.ruleAt(modelRow).getId())) {
                rulesTable.setRowSelectionInterval(viewRow, viewRow);
                rulesTable.scrollRectToVisible(rulesTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
        // Not visible under the current search/family/severity filters — nothing sensible to select.
        rulesTable.clearSelection();
        detailArea.setText("Select a rule to see full details.");
    }

    private void onSave() {
        if (currentRuleSet == null || currentRulesPath == null) {
            return;
        }
        saveTo(currentRulesPath);
    }

    private void onSaveAs() {
        if (currentRuleSet == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Rules JSON", "json"));
        chooser.setSelectedFile(currentRulesPath != null ? currentRulesPath.toFile() : new File("rules.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (!target.toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            target = target.resolveSibling(target.getFileName() + ".json");
        }
        if (Files.exists(target)) {
            int overwrite = JOptionPane.showConfirmDialog(this, target + " already exists. Overwrite?",
                "Confirm overwrite", JOptionPane.YES_NO_OPTION);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        saveTo(target);
    }

    private void saveTo(Path target) {
        ValidationResult result = validator.validate(currentRuleSet);
        if (!result.isValid()) {
            ValidationDialogs.show(this, "Cannot save: rules failed validation", result);
            return;
        }
        try {
            new RulesWriter().write(target, currentRuleSet);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to write rules file:\n" + e.getMessage(),
                "Save failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!result.getWarnings().isEmpty()) {
            ValidationDialogs.show(this, "Saved with warnings", result);
        }
        onSaved.accept(target);
    }

    private void updateHeader(RuleSet ruleSet, Path rulesPath) {
        if (ruleSet == null) {
            sourceLabel.setText("No rules loaded.");
            weightsLabel.setText(" ");
            thresholdsLabel.setText(" ");
            return;
        }
        String source = rulesPath == null ? "bundled default rules" : rulesPath.toString();
        long enabledCount = ruleSet.getRules().stream().filter(Rule::isEnabled).count();
        sourceLabel.setText("Source: " + source + "    Schema v" + ruleSet.getSchemaVersion()
            + "    " + enabledCount + " enabled / " + ruleSet.getRules().size() + " total rules");

        StringBuilder weights = new StringBuilder("Severity weights:  ");
        ruleSet.getSeverityWeights().forEach((severity, weight) ->
            weights.append(severity.json()).append('=').append(weight).append("   "));
        weightsLabel.setText(weights.toString().stripTrailing());

        VerdictThresholds t = ruleSet.getVerdictThresholds();
        thresholdsLabel.setText("Verdict thresholds:  Low-risk ≥ " + t.getLowRisk()
            + "   Suspicious ≥ " + t.getSuspicious()
            + "   Likely compromised ≥ " + t.getLikelyCompromised());
    }

    private void applyFilters() {
        if (currentRuleSet == null) {
            rulesTableModel.setRules(List.of(), null);
            tabs.setTitleAt(0, "Rules");
            return;
        }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String familySel = (String) familyCombo.getSelectedItem();
        String severitySel = (String) severityCombo.getSelectedItem();
        boolean enabledOnly = enabledOnlyCheck.isSelected();

        List<Rule> filtered = new ArrayList<>();
        for (Rule rule : currentRuleSet.getRules()) {
            if (enabledOnly && !rule.isEnabled()) {
                continue;
            }
            if (!ALL_FAMILIES.equals(familySel)
                && (rule.getFamily() == null || !rule.getFamily().json().equalsIgnoreCase(familySel))) {
                continue;
            }
            if (!ALL_SEVERITIES.equals(severitySel)
                && (rule.getSeverity() == null || !rule.getSeverity().json().equalsIgnoreCase(severitySel))) {
                continue;
            }
            if (!query.isEmpty() && !matchesQuery(rule, query)) {
                continue;
            }
            filtered.add(rule);
        }
        rulesTableModel.setRules(filtered, currentRuleSet);
        tabs.setTitleAt(0, "Rules (" + filtered.size() + "/" + currentRuleSet.getRules().size() + ")");
        tabs.setTitleAt(1, "Combos (" + currentRuleSet.getCombos().size() + ")");
    }

    private boolean matchesQuery(Rule rule, String query) {
        if (rule.getId() != null && rule.getId().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (rule.getName() != null && rule.getName().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (rule.getDescription() != null && rule.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (String tag : rule.getTags()) {
            if (tag.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private void showSelectedRuleDetail() {
        Rule selected = selectedRule();
        if (selected == null) {
            return;
        }
        detailArea.setText(formatDetail(selected));
        detailArea.setCaretPosition(0);
    }

    private String formatDetail(Rule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule.getName()).append("  (").append(rule.getId()).append(")\n");
        sb.append("Family: ").append(rule.getFamily() == null ? "-" : rule.getFamily().json());
        sb.append("   Type: ").append(rule.getType() == null ? "-" : rule.getType().json());
        sb.append("   Severity: ").append(rule.getSeverity() == null ? "-" : rule.getSeverity().json());
        sb.append("   Weight: ").append(currentRuleSet == null ? "-" : currentRuleSet.weightFor(rule)).append('\n');
        sb.append("Enabled: ").append(rule.isEnabled() ? "yes" : "no").append('\n');
        sb.append("Channels: ").append(rule.appliesToAllChannels() ? "* (all)" : String.join(", ", rule.getChannels())).append('\n');
        sb.append("Tags: ").append(rule.getTags().isEmpty() ? "-" : String.join(", ", rule.getTags())).append('\n');

        if (rule.getPattern() != null) {
            sb.append("\nPattern: ").append(rule.getPattern()).append('\n');
            sb.append("Case sensitive: ").append(rule.isCaseSensitive() ? "yes" : "no").append('\n');
        }
        if (!rule.getKeywords().isEmpty()) {
            sb.append("\nKeywords: ").append(String.join(", ", rule.getKeywords())).append('\n');
        }
        if (rule.getDetector() != null) {
            sb.append("\nDetector: ").append(rule.getDetector()).append('\n');
        }
        if (!rule.getParams().isEmpty()) {
            sb.append("Params:\n");
            rule.getParams().forEach((key, value) -> sb.append("  ").append(key).append(" = ").append(value).append('\n'));
        }

        sb.append("\nDescription:\n").append(rule.getDescription() == null ? "-" : rule.getDescription()).append('\n');
        sb.append("\nRemediation:\n").append(rule.getRemediation() == null ? "-" : rule.getRemediation()).append('\n');
        return sb.toString();
    }
}
