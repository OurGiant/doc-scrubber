package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.rules.Rule;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.VerdictThresholds;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only inspector for the currently loaded ruleset — what a "poison pill" rule actually checks
 * for, in plain view, since the whole rules engine is meant to be inspectable rather than opaque.
 * Non-modal so it can stay open while scanning, and {@link #refresh} is called by {@code MainWindow}
 * whenever rules are (re)loaded so it never shows a stale ruleset without the user asking.
 */
final class RulesWindow extends JDialog {

    private static final String ALL_FAMILIES = "All Families";
    private static final String ALL_SEVERITIES = "All Severities";

    private final JLabel sourceLabel = new JLabel(" ");
    private final JLabel weightsLabel = new JLabel(" ");
    private final JLabel thresholdsLabel = new JLabel(" ");

    private final JTextField searchField = new JTextField(18);
    private final JComboBox<String> familyCombo = new JComboBox<>(new String[]{ALL_FAMILIES, "Content", "Structural"});
    private final JComboBox<String> severityCombo =
        new JComboBox<>(new String[]{ALL_SEVERITIES, "Info", "Low", "Medium", "High", "Critical"});
    private final JCheckBox enabledOnlyCheck = new JCheckBox("Enabled only");

    private final RulesTableModel rulesTableModel = new RulesTableModel();
    private final JTable rulesTable = new JTable(rulesTableModel);
    private final JTextArea detailArea = new JTextArea();

    private final CombosTableModel combosTableModel = new CombosTableModel();
    private final JTable combosTable = new JTable(combosTableModel);

    private final JTabbedPane tabs = new JTabbedPane();

    private RuleSet currentRuleSet;

    RulesWindow(Frame parent) {
        super(parent, "Rules", false);
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

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(filterBar, BorderLayout.NORTH);
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

    /** Called by {@code MainWindow} on open and every time the ruleset is (re)loaded. */
    void refresh(RuleSet ruleSet, Path rulesPath) {
        this.currentRuleSet = ruleSet;
        setTitle(ruleSet == null ? "Rules"
            : "Rules — " + (rulesPath == null ? "bundled default" : rulesPath.toString()));
        updateHeader(ruleSet, rulesPath);
        combosTableModel.setCombos(ruleSet == null ? List.of() : ruleSet.getCombos());
        rulesTable.clearSelection();
        detailArea.setText("Select a rule to see full details.");
        applyFilters();
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
        int viewRow = rulesTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = rulesTable.convertRowIndexToModel(viewRow);
        detailArea.setText(formatDetail(rulesTableModel.ruleAt(modelRow)));
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
