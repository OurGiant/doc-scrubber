package com.ourgiant.docscrubber.gui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.rules.Rule;
import com.ourgiant.docscrubber.rules.RuleFamily;
import com.ourgiant.docscrubber.rules.RuleType;
import com.ourgiant.docscrubber.rules.Severity;
import com.ourgiant.docscrubber.rules.UnicodeRanges;
import com.ourgiant.docscrubber.rules.detector.DetectorRegistry;
import com.ourgiant.docscrubber.util.JsonMapperFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Add/Edit/Duplicate form for a single rule. Rule/RuleSet stay immutable — this dialog only ever
 * hands back a freshly-built {@link Rule} via {@link #getResult()}; the caller decides how to fold
 * it into the working ruleset. Every field here maps directly onto a {@code rules.json} property,
 * since rules stay data, never code.
 */
final class RuleEditDialog extends JDialog {

    private static final RuleType[] CONTENT_TYPES = {RuleType.REGEX, RuleType.KEYWORD_LIST, RuleType.UNICODE_CLASS};
    private static final RuleType[] STRUCTURAL_TYPES = {RuleType.DETECTOR};

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();
    private final Set<String> reservedIds;

    private final JTextField idField = new JTextField(22);
    private final JTextField nameField = new JTextField(30);
    private final JComboBox<RuleFamily> familyCombo = new JComboBox<>(RuleFamily.values());
    private final JComboBox<RuleType> typeCombo = new JComboBox<>(CONTENT_TYPES);
    private final JComboBox<Severity> severityCombo = new JComboBox<>(Severity.values());
    private final JTextField weightField = new JTextField(6);
    private final JCheckBox enabledCheck = new JCheckBox("Enabled");
    private final JCheckBox allChannelsCheck = new JCheckBox("All channels (*)");
    private final Map<Channel, JCheckBox> channelChecks = new EnumMap<>(Channel.class);
    private final JTextField tagsField = new JTextField(30);
    private final JTextArea descriptionArea = new JTextArea(3, 30);
    private final JTextField remediationField = new JTextField(30);

    private final JTextField patternField = new JTextField(30);
    private final JCheckBox caseSensitiveCheck = new JCheckBox("Case sensitive");
    private final JTextArea keywordsArea = new JTextArea(4, 30);
    private final JComboBox<String> detectorCombo = new JComboBox<>();
    private final JTextArea paramsArea = new JTextArea(6, 30);

    private JPanel patternRow;
    private JPanel caseSensitiveRow;
    private JPanel keywordsSection;
    private JPanel detectorRow;
    private JPanel paramsSection;

    private final JLabel errorLabel = new JLabel(" ");

    private Rule result;

    RuleEditDialog(Dialog owner, String title, Rule initial, Set<String> reservedIds, DetectorRegistry detectorRegistry) {
        super(owner, title, true);
        this.reservedIds = reservedIds;

        for (Channel channel : Channel.values()) {
            channelChecks.put(channel, new JCheckBox(channel.name()));
        }
        detectorCombo.setEditable(true);
        detectorRegistry.knownIds().stream().sorted().forEach(detectorCombo::addItem);
        detectorCombo.setSelectedItem(null);

        familyCombo.setRenderer(new EnumRenderer());
        typeCombo.setRenderer(new EnumRenderer());
        severityCombo.setRenderer(new EnumRenderer());

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(buildFormPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        familyCombo.addActionListener(e -> onFamilyChanged());
        typeCombo.addActionListener(e -> updateTypeSpecificVisibility());
        allChannelsCheck.addActionListener(e -> updateChannelCheckboxesEnabled());

        if (initial != null) {
            populateFrom(initial);
        } else {
            enabledCheck.setSelected(true);
            allChannelsCheck.setSelected(true);
            severityCombo.setSelectedItem(Severity.MEDIUM);
            updateChannelCheckboxesEnabled();
            updateTypeSpecificVisibility();
        }

        setSize(600, 680);
        setLocationRelativeTo(owner);
    }

    /** Called by the caller before {@code setVisible(true)} for a "Duplicate" flow — keeps the copy's fields but forces a fresh id. */
    void clearIdField() {
        idField.setText("");
    }

    Rule getResult() {
        return result;
    }

    private JScrollPane buildFormPanel() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        errorLabel.setForeground(new Color(0xC0, 0x20, 0x20));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(errorLabel);

        form.add(formRow("ID:", idField));
        form.add(formRow("Name:", nameField));
        form.add(formRow("Family:", familyCombo));
        form.add(formRow("Type:", typeCombo));
        form.add(formRow("Severity:", severityCombo));
        form.add(formRow("Weight override (blank = use severity weight):", weightField));
        form.add(formRow("", enabledCheck));

        form.add(formRow("Channels:", allChannelsCheck));
        JPanel channelsGrid = new JPanel(new GridLayout(0, 3, 4, 2));
        for (Channel channel : Channel.values()) {
            channelsGrid.add(channelChecks.get(channel));
        }
        JPanel channelsRow = new JPanel(new BorderLayout());
        channelsRow.setBorder(BorderFactory.createEmptyBorder(0, 146, 6, 0));
        channelsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        channelsRow.add(channelsGrid, BorderLayout.CENTER);
        form.add(channelsRow);

        form.add(formRow("Tags (comma-separated):", tagsField));

        form.add(sectionLabel("Description"));
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(480, 70));
        descScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(descScroll);

        form.add(formRow("Remediation:", remediationField));

        form.add(sectionLabel("Type-specific"));

        patternRow = formRow("Pattern (regex):", patternField);
        form.add(patternRow);
        caseSensitiveRow = formRow("", caseSensitiveCheck);
        form.add(caseSensitiveRow);

        keywordsSection = new JPanel();
        keywordsSection.setLayout(new BoxLayout(keywordsSection, BoxLayout.Y_AXIS));
        keywordsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        keywordsSection.add(sectionLabel("Keywords (one per line)"));
        keywordsArea.setLineWrap(true);
        JScrollPane keywordsScroll = new JScrollPane(keywordsArea);
        keywordsScroll.setPreferredSize(new Dimension(480, 80));
        keywordsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        keywordsSection.add(keywordsScroll);
        form.add(keywordsSection);

        detectorRow = formRow("Detector ID:", detectorCombo);
        form.add(detectorRow);

        paramsSection = new JPanel();
        paramsSection.setLayout(new BoxLayout(paramsSection, BoxLayout.Y_AXIS));
        paramsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        paramsSection.add(sectionLabel("Params (JSON object)"));
        paramsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane paramsScroll = new JScrollPane(paramsArea);
        paramsScroll.setPreferredSize(new Dimension(480, 110));
        paramsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        paramsSection.add(paramsScroll);
        form.add(paramsSection);

        JScrollPane outerScroll = new JScrollPane(form);
        outerScroll.setBorder(null);
        outerScroll.getVerticalScrollBar().setUnitIncrement(16);
        return outerScroll;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> onSaveClicked());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        panel.add(saveButton);
        panel.add(cancelButton);
        return panel;
    }

    private JPanel formRow(String label, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(220, l.getPreferredSize().height));
        row.add(l);
        row.add(field);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        return label;
    }

    private void onFamilyChanged() {
        RuleFamily family = (RuleFamily) familyCombo.getSelectedItem();
        RuleType[] options = family == RuleFamily.STRUCTURAL ? STRUCTURAL_TYPES : CONTENT_TYPES;
        typeCombo.setModel(new DefaultComboBoxModel<>(options));
        updateTypeSpecificVisibility();
    }

    private void updateTypeSpecificVisibility() {
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        boolean isRegex = type == RuleType.REGEX;
        boolean isKeyword = type == RuleType.KEYWORD_LIST;
        boolean isUnicode = type == RuleType.UNICODE_CLASS;
        boolean isDetector = type == RuleType.DETECTOR;

        patternRow.setVisible(isRegex);
        caseSensitiveRow.setVisible(isRegex || isKeyword);
        keywordsSection.setVisible(isKeyword);
        detectorRow.setVisible(isDetector);
        paramsSection.setVisible(isUnicode || isDetector);

        revalidate();
        repaint();
    }

    private void updateChannelCheckboxesEnabled() {
        boolean allSelected = allChannelsCheck.isSelected();
        channelChecks.values().forEach(cb -> cb.setEnabled(!allSelected));
    }

    private void populateFrom(Rule r) {
        idField.setText(r.getId());
        nameField.setText(r.getName());
        familyCombo.setSelectedItem(r.getFamily());
        onFamilyChanged();
        typeCombo.setSelectedItem(r.getType());
        severityCombo.setSelectedItem(r.getSeverity());
        weightField.setText(r.getWeight() == null ? "" : String.valueOf(r.getWeight()));
        enabledCheck.setSelected(r.isEnabled());

        if (r.appliesToAllChannels()) {
            allChannelsCheck.setSelected(true);
        } else {
            allChannelsCheck.setSelected(false);
            for (String c : r.getChannels()) {
                try {
                    JCheckBox box = channelChecks.get(Channel.valueOf(c.toUpperCase(Locale.ROOT)));
                    if (box != null) {
                        box.setSelected(true);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Unknown channel name from a hand-edited file — leave unchecked rather than fail to open the editor.
                }
            }
        }
        updateChannelCheckboxesEnabled();

        tagsField.setText(String.join(", ", r.getTags()));
        descriptionArea.setText(r.getDescription() == null ? "" : r.getDescription());
        remediationField.setText(r.getRemediation() == null ? "" : r.getRemediation());
        patternField.setText(r.getPattern() == null ? "" : r.getPattern());
        caseSensitiveCheck.setSelected(r.isCaseSensitive());
        keywordsArea.setText(String.join("\n", r.getKeywords()));
        detectorCombo.getEditor().setItem(r.getDetector() == null ? "" : r.getDetector());
        if (!r.getParams().isEmpty()) {
            try {
                paramsArea.setText(mapper.writeValueAsString(r.getParams()));
            } catch (JsonProcessingException e) {
                paramsArea.setText(String.valueOf(r.getParams()));
            }
        }

        updateTypeSpecificVisibility();
    }

    private void onSaveClicked() {
        errorLabel.setText(" ");

        String id = idField.getText().trim();
        if (id.isEmpty()) {
            showError("ID is required.");
            return;
        }
        if (reservedIds.contains(id)) {
            showError("ID '" + id + "' is already used by another rule.");
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError("Name is required.");
            return;
        }

        RuleFamily family = (RuleFamily) familyCombo.getSelectedItem();
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        Severity severity = (Severity) severityCombo.getSelectedItem();

        Integer weight = null;
        String weightText = weightField.getText().trim();
        if (!weightText.isEmpty()) {
            try {
                weight = Integer.valueOf(weightText);
            } catch (NumberFormatException e) {
                showError("Weight override must be a whole number.");
                return;
            }
            if (weight <= 0) {
                showError("Weight override must be positive.");
                return;
            }
        }

        List<String> channels;
        if (allChannelsCheck.isSelected()) {
            channels = List.of("*");
        } else {
            channels = new ArrayList<>();
            for (Map.Entry<Channel, JCheckBox> entry : channelChecks.entrySet()) {
                if (entry.getValue().isSelected()) {
                    channels.add(entry.getKey().name());
                }
            }
            if (channels.isEmpty()) {
                showError("Select at least one channel, or check \"All channels\".");
                return;
            }
        }

        String pattern = null;
        List<String> keywords = List.of();
        boolean caseSensitive = caseSensitiveCheck.isSelected();
        String detector = null;
        Map<String, Object> params = Map.of();

        if (type == RuleType.REGEX) {
            pattern = patternField.getText().trim();
            if (pattern.isEmpty()) {
                showError("Pattern is required for a regex rule.");
                return;
            }
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                showError("Invalid regex pattern: " + e.getMessage());
                return;
            }
        } else if (type == RuleType.KEYWORD_LIST) {
            keywords = parseLines(keywordsArea.getText());
            if (keywords.isEmpty()) {
                showError("Enter at least one keyword, one per line.");
                return;
            }
        } else if (type == RuleType.UNICODE_CLASS) {
            try {
                params = parseParams(paramsArea.getText());
            } catch (JsonProcessingException e) {
                showError("Params must be valid JSON: " + e.getOriginalMessage());
                return;
            }
            try {
                UnicodeRanges.parse(params);
            } catch (IllegalArgumentException e) {
                showError(e.getMessage());
                return;
            }
        } else if (type == RuleType.DETECTOR) {
            Object detectorItem = detectorCombo.getEditor().getItem();
            detector = detectorItem == null ? "" : detectorItem.toString().trim();
            if (detector.isEmpty()) {
                showError("Detector ID is required.");
                return;
            }
            try {
                params = parseParams(paramsArea.getText());
            } catch (JsonProcessingException e) {
                showError("Params must be valid JSON: " + e.getOriginalMessage());
                return;
            }
        }

        List<String> tags = parseCommaSeparated(tagsField.getText());
        String description = descriptionArea.getText().trim();
        String remediation = remediationField.getText().trim();
        boolean enabled = enabledCheck.isSelected();

        result = new Rule(id, name, family, type, pattern, keywords, caseSensitive, detector, params, channels,
            severity, weight, description, remediation, enabled, tags);
        dispose();
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private Map<String, Object> parseParams(String text) throws JsonProcessingException {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return Map.of();
        }
        return mapper.readValue(trimmed, new TypeReference<Map<String, Object>>() { });
    }

    private List<String> parseLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private List<String> parseCommaSeparated(String text) {
        List<String> values = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static final class EnumRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text = switch (value) {
                case RuleFamily f -> capitalize(f.json());
                case RuleType t -> capitalize(t.json());
                case Severity s -> capitalize(s.json());
                case null, default -> String.valueOf(value);
            };
            setText(text);
            return c;
        }

        private String capitalize(String s) {
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
