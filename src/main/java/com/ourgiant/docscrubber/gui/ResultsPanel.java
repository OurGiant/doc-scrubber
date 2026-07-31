package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.DocumentScanner.ScanResult;
import com.ourgiant.docscrubber.ScanLimits;
import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.rules.Severity;
import com.ourgiant.docscrubber.score.ScoreReport;
import com.ourgiant.docscrubber.score.Verdict;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Verdict banner, honest-limitations notice (always shown for PDFs — see
 * {@code PdfParser}), and the findings table. The limitations notice is never conditional on
 * severity or score: a clean-looking PDF must still tell the user detection was heuristic.
 */
final class ResultsPanel extends JPanel {

    private final JLabel verdictLabel = new JLabel(" ");
    private final JLabel scoreLabel = new JLabel(" ");
    private final JPanel bannerPanel = new JPanel(new BorderLayout());
    private final JTextArea limitationsArea = new JTextArea();
    private final JPanel limitationsPanel = new JPanel(new BorderLayout());
    private final FindingsTableModel tableModel = new FindingsTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextArea detailArea = new JTextArea();
    private final JLabel emptyLabel = new JLabel(" ", SwingConstants.CENTER);
    private Supplier<String> rulesSummaryResolver = () -> "";

    ResultsPanel() {
        super(new BorderLayout());

        bannerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        verdictLabel.setFont(verdictLabel.getFont().deriveFont(Font.BOLD, 18f));
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.PLAIN, 14f));
        JPanel bannerText = new JPanel(new GridLayout(2, 1));
        bannerText.setOpaque(false);
        bannerText.add(verdictLabel);
        bannerText.add(scoreLabel);
        bannerPanel.add(bannerText, BorderLayout.WEST);

        limitationsArea.setEditable(false);
        limitationsArea.setLineWrap(true);
        limitationsArea.setWrapStyleWord(true);
        limitationsArea.setOpaque(false);
        limitationsArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        limitationsPanel.add(limitationsArea, BorderLayout.CENTER);
        limitationsPanel.setBackground(new Color(0xFF, 0xF3, 0xCD));
        limitationsPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(0xE0, 0xA8, 0x00)));
        limitationsPanel.setVisible(false);

        table.setRowHeight(22);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedDetail();
            }
        });
        table.setDefaultRenderer(Object.class, new SeverityRowRenderer());

        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(bannerPanel, BorderLayout.NORTH);
        topPanel.add(limitationsPanel, BorderLayout.SOUTH);

        JSplitPane tableAndDetail = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table), new JScrollPane(detailArea));
        tableAndDetail.setResizeWeight(0.7);
        tableAndDetail.setContinuousLayout(true);

        add(topPanel, BorderLayout.NORTH);
        add(tableAndDetail, BorderLayout.CENTER);

        showEmptyState();
    }

    /** Called once by {@code MainWindow} so the empty state can show live rule-count/source text without polling. */
    void setRulesSummaryResolver(Supplier<String> resolver) {
        this.rulesSummaryResolver = resolver;
    }

    void showEmptyState() {
        removeAll();
        emptyLabel.setText(emptyStateHtml());
        add(emptyLabel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private String emptyStateHtml() {
        return "<html><body style='text-align:center; font-family:sans-serif; padding:24px;'>"
            + "<h2 style='margin-bottom:4px;'>DocScrubber</h2>"
            + "<p style='color:#666; margin-top:0;'>Scans documents for prompt-injection &quot;poison "
            + "pills&quot; hidden from human readers but meant for AI tools.</p>"
            + "<p style='text-align:left; display:inline-block; margin-top:16px;'>"
            + "<b>Getting started</b><br>"
            + "1.&nbsp;File &rarr; Add Files... to queue a document<br>"
            + "2.&nbsp;Select it in the list to see its scan result here<br>"
            + "3.&nbsp;Rules &rarr; View Rules... to see what's being checked for</p>"
            + "<p style='color:#888; font-size:11px; margin-top:20px;'>" + rulesSummaryResolver.get()
            + "<br>Files over " + ScanLimits.describeMaxFileSize() + " are skipped for safety.</p>"
            + "</body></html>";
    }

    void show(ScanResult result) {
        removeAll();
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(bannerPanel, BorderLayout.NORTH);
        topPanel.add(limitationsPanel, BorderLayout.SOUTH);
        JSplitPane tableAndDetail = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table), new JScrollPane(detailArea));
        tableAndDetail.setResizeWeight(0.7);
        tableAndDetail.setContinuousLayout(true);
        add(topPanel, BorderLayout.NORTH);
        add(tableAndDetail, BorderLayout.CENTER);

        ScoreReport report = result.report();
        verdictLabel.setText(report.getVerdict().display());
        scoreLabel.setText("Score: " + report.getScore() + " / 100 — " + result.file().getFileName());
        bannerPanel.setBackground(verdictColor(report.getVerdict()));

        if (report.hasLimitations()) {
            StringBuilder sb = new StringBuilder("Detection limitations for this ")
                .append(formatLabel(result.format())).append(" scan:\n");
            for (String limitation : report.getLimitations()) {
                sb.append("• ").append(limitation).append('\n');
            }
            limitationsArea.setText(sb.toString().stripTrailing());
            limitationsPanel.setVisible(true);
        } else {
            limitationsPanel.setVisible(false);
        }

        tableModel.setFindings(report.getFindings());
        detailArea.setText(report.getFindings().isEmpty() ? "No findings." : "");

        revalidate();
        repaint();
    }

    private void showSelectedDetail() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Finding f = tableModel.findingAt(modelRow);
        detailArea.setText(
            "Rule: " + f.getRuleName() + " (" + f.getRuleId() + ")\n"
                + "Severity: " + f.getSeverity().json() + "  Weight: " + f.getWeight() + "\n"
                + "Channel: " + f.getChannel().name() + "\n"
                + "Location: " + f.getLocation().describe() + "\n"
                + "Remediation: " + f.getRemediation() + "\n"
                + "Tags: " + String.join(", ", f.getTags()) + "\n\n"
                + "Evidence (hidden/invisible characters shown as [MARKERS]):\n" + f.getEvidence()
        );
        detailArea.setCaretPosition(0);
    }

    private String formatLabel(DocumentFormat format) {
        return switch (format) {
            case PDF -> "PDF";
            case DOCX -> "docx";
            case MARKDOWN -> "Markdown";
            case PLAIN_TEXT -> "text";
            case JSON -> "JSON";
            case YAML -> "YAML";
            case XML -> "XML";
        };
    }

    private Color verdictColor(Verdict verdict) {
        return switch (verdict) {
            case CLEAN -> new Color(0xE6, 0xF4, 0xEA);
            case LOW_RISK -> new Color(0xFF, 0xF8, 0xE1);
            case SUSPICIOUS -> new Color(0xFF, 0xE8, 0xCC);
            case LIKELY_COMPROMISED -> new Color(0xFD, 0xE0, 0xE0);
        };
    }

    private static final class SeverityRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                Severity severity = ((FindingsTableModel) table.getModel()).findingAt(modelRow).getSeverity();
                c.setBackground(severityColor(severity));
            }
            return c;
        }

        private Color severityColor(Severity severity) {
            return switch (severity) {
                case CRITICAL -> new Color(0xFD, 0xE0, 0xE0);
                case HIGH -> new Color(0xFF, 0xE8, 0xCC);
                case MEDIUM -> new Color(0xFF, 0xF8, 0xE1);
                case LOW, INFO -> Color.WHITE;
            };
        }
    }
}
