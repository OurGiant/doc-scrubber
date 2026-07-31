package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.AppPreferences;
import com.ourgiant.docscrubber.DocumentScanner;
import com.ourgiant.docscrubber.DocumentScanner.ScanResult;
import com.ourgiant.docscrubber.ThemeManager;
import com.ourgiant.docscrubber.engine.RulesEngine;
import com.ourgiant.docscrubber.parser.ParserRegistry;
import com.ourgiant.docscrubber.report.HtmlReportWriter;
import com.ourgiant.docscrubber.report.JsonReportWriter;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.RulesLoader;
import com.ourgiant.docscrubber.rules.RulesValidator;
import com.ourgiant.docscrubber.rules.ValidationResult;
import com.ourgiant.docscrubber.rules.detector.DetectorRegistry;
import com.ourgiant.docscrubber.score.Scorer;
import com.ourgiant.docscrubber.util.AppVersion;
import com.ourgiant.docscrubber.util.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MainWindow extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    private final DetectorRegistry detectorRegistry = new DetectorRegistry();
    private final RulesLoader rulesLoader = new RulesLoader();
    private final RulesValidator rulesValidator = new RulesValidator(detectorRegistry);
    private final ParserRegistry parserRegistry = new ParserRegistry();
    private final DocumentScanner scanner = new DocumentScanner(parserRegistry, new RulesEngine(detectorRegistry), new Scorer());

    private final AppPreferences preferences = new AppPreferences();

    private final Map<Path, ScanResult> results = new ConcurrentHashMap<>();
    private final Map<Path, String> errors = new ConcurrentHashMap<>();

    private final ScanQueuePanel queuePanel = new ScanQueuePanel(parserRegistry);
    private final ResultsPanel resultsPanel = new ResultsPanel();
    private final JLabel statusBar = new JLabel(" ");

    private volatile RuleSet currentRuleSet;
    private volatile Path currentRulesPath;
    private volatile Path selectedFile;

    public MainWindow() {
        setTitle("DocScrubber");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 720);
        setLocationRelativeTo(null);
        setAppIcon();

        setJMenuBar(buildMenuBar());

        queuePanel.setOnFileAdded(this::scanAsync);
        queuePanel.setOnSelectionChanged(this::onSelectionChanged);
        queuePanel.setStatusResolver(this::statusFor);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, queuePanel, resultsPanel);
        split.setDividerLocation(320);
        // Non-continuous layout (the Swing default) only paints an XOR outline while dragging and
        // resizes on release, which reads as "the drag did nothing" until the exact moment the
        // mouse button comes up. Live-resize instead so the panel visibly follows the cursor.
        split.setContinuousLayout(true);

        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        loadRules(null);
        checkForUpdateAndNotifyIfNewer();
    }

    private void setAppIcon() {
        java.net.URL iconUrl = MainWindow.class.getResource("/app-icon.png");
        if (iconUrl != null) {
            setIconImage(new ImageIcon(iconUrl).getImage());
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem addFiles = new JMenuItem("Add Files...");
        addFiles.addActionListener(e -> queuePanel.chooseFiles());
        JMenuItem exportReport = new JMenuItem("Export Report...");
        exportReport.addActionListener(e -> exportReport());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        fileMenu.add(addFiles);
        fileMenu.add(exportReport);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu rulesMenu = new JMenu("Rules");
        JMenuItem loadRulesFile = new JMenuItem("Load Rules File...");
        loadRulesFile.addActionListener(e -> chooseRulesFile());
        JMenuItem reloadRules = new JMenuItem("Reload Rules");
        reloadRules.addActionListener(e -> loadRules(currentRulesPath));
        JMenuItem useDefaultRules = new JMenuItem("Use Bundled Default Rules");
        useDefaultRules.addActionListener(e -> loadRules(null));
        rulesMenu.add(loadRulesFile);
        rulesMenu.add(reloadRules);
        rulesMenu.add(useDefaultRules);

        JMenu viewMenu = new JMenu("View");
        JMenu themeMenu = new JMenu("Theme");
        for (String themeName : ThemeManager.getAvailableThemeNames()) {
            JMenuItem item = new JMenuItem(themeName);
            item.addActionListener(e -> ThemeManager.applyTheme(themeName));
            themeMenu.add(item);
        }
        viewMenu.add(themeMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem viewLogs = new JMenuItem("View Logs...");
        viewLogs.addActionListener(e -> new LogViewerDialog(this).setVisible(true));
        helpMenu.add(viewLogs);
        helpMenu.addSeparator();
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> new AboutDialog(this).setVisible(true));
        helpMenu.add(about);

        menuBar.add(fileMenu);
        menuBar.add(rulesMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private void chooseRulesFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Rules JSON", "json"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadRules(chooser.getSelectedFile().toPath());
        }
    }

    private void loadRules(Path rulesPath) {
        try {
            RuleSet loaded = rulesPath == null ? rulesLoader.loadDefault() : rulesLoader.load(rulesPath);
            ValidationResult validation = rulesValidator.validate(loaded);
            if (!validation.isValid()) {
                showRulesError(rulesPath, validation);
                return;
            }
            if (!validation.getWarnings().isEmpty()) {
                logger.warn("Rules loaded with warnings from {}: {}", describeRulesSource(rulesPath), validation.getWarnings());
            }
            currentRuleSet = loaded;
            currentRulesPath = rulesPath;
            updateStatusBar();
            logger.info("Loaded {} rules from {}", loaded.getRules().size(), describeRulesSource(rulesPath));

            // Existing results were scored against the previous ruleset; keep them visible but
            // stale results would be misleading, so re-scan everything against the new rules.
            for (Path file : results.keySet()) {
                scanAsync(file);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Failed to read rules file:\n" + e.getMessage(),
                "Rules load error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showRulesError(Path rulesPath, ValidationResult validation) {
        String message = "Refusing to scan: " + describeRulesSource(rulesPath) + " failed validation:\n\n"
            + String.join("\n", validation.getErrors());
        JOptionPane.showMessageDialog(this, message, "Invalid rules.json", JOptionPane.ERROR_MESSAGE);
        logger.error("Rules validation failed for {}: {}", describeRulesSource(rulesPath), validation.getErrors());
    }

    private String describeRulesSource(Path rulesPath) {
        return rulesPath == null ? "bundled default rules" : rulesPath.toString();
    }

    private void updateStatusBar() {
        String rulesLabel = currentRulesPath == null ? "bundled default" : currentRulesPath.toString();
        int count = currentRuleSet == null ? 0 : currentRuleSet.getRules().size();
        statusBar.setText("Rules: " + rulesLabel + "  —  " + count + " rules loaded");
    }

    /**
     * Silent unless there's actually something to say: no UI at all if up to date or the check
     * fails, and only once per newly-released version (not once per launch) if there's an
     * update — otherwise a known update sitting unapplied would pop this on every single
     * startup. A user can still always check manually via Help > About regardless of this
     * state. This is the app's only outbound network call.
     */
    private void checkForUpdateAndNotifyIfNewer() {
        SwingWorker<Optional<UpdateChecker.ReleaseInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<UpdateChecker.ReleaseInfo> doInBackground() {
                return UpdateChecker.fetchLatestRelease();
            }

            @Override
            protected void done() {
                Optional<UpdateChecker.ReleaseInfo> release;
                try {
                    release = get();
                } catch (Exception e) {
                    logger.warn("Silent startup update check failed", e);
                    return;
                }
                if (release.isEmpty()) {
                    return;
                }
                UpdateChecker.ReleaseInfo info = release.get();
                if (!UpdateChecker.isNewerVersion(info.version(), AppVersion.resolve())) {
                    return;
                }
                if (info.version().equals(preferences.getLastNotifiedUpdateVersion())) {
                    return;
                }
                preferences.setLastNotifiedUpdateVersion(info.version());
                new AboutDialog(MainWindow.this, info).setVisible(true);
            }
        };
        worker.execute();
    }

    private void scanAsync(Path file) {
        RuleSet ruleSet = currentRuleSet;
        if (ruleSet == null) {
            errors.put(file, "no valid rules loaded");
            queuePanel.refreshStatus();
            return;
        }
        errors.remove(file);
        queuePanel.refreshStatus();

        new SwingWorker<ScanResult, Void>() {
            @Override
            protected ScanResult doInBackground() throws IOException {
                return scanner.scan(file, ruleSet);
            }

            @Override
            protected void done() {
                try {
                    ScanResult result = get();
                    results.put(file, result);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    errors.put(file, cause.getMessage() != null ? cause.getMessage() : cause.toString());
                    logger.warn("Scan failed for {}", file, cause);
                }
                queuePanel.refreshStatus();
                if (file.equals(selectedFile)) {
                    onSelectionChanged(file);
                }
            }
        }.execute();
    }

    private String statusFor(Path file) {
        ScanResult result = results.get(file);
        if (result != null) {
            return result.report().getVerdict().display() + " (" + result.report().getScore() + ")";
        }
        String error = errors.get(file);
        if (error != null) {
            return "Error: " + error;
        }
        return "scanning...";
    }

    private void onSelectionChanged(Path file) {
        selectedFile = file;
        if (file == null) {
            resultsPanel.showEmptyState();
            return;
        }
        ScanResult result = results.get(file);
        if (result != null) {
            resultsPanel.show(result);
        } else {
            resultsPanel.showEmptyState();
        }
    }

    private void exportReport() {
        Path file = selectedFile;
        ScanResult result = file == null ? null : results.get(file);
        if (result == null) {
            JOptionPane.showMessageDialog(this, "Select a scanned document first.", "Nothing to export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("JSON report", "json");
        FileNameExtensionFilter htmlFilter = new FileNameExtensionFilter("HTML report", "html");
        chooser.addChoosableFileFilter(jsonFilter);
        chooser.addChoosableFileFilter(htmlFilter);
        chooser.setFileFilter(htmlFilter);
        chooser.setSelectedFile(new java.io.File(file.getFileName() + ".report.html"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        boolean asJson = chooser.getFileFilter() == jsonFilter || target.toString().toLowerCase().endsWith(".json");

        try {
            if (Files.exists(target)) {
                int overwrite = JOptionPane.showConfirmDialog(this, target + " already exists. Overwrite?", "Confirm overwrite", JOptionPane.YES_NO_OPTION);
                if (overwrite != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            if (asJson) {
                new JsonReportWriter().write(target, file, result.report());
            } else {
                new HtmlReportWriter().write(target, file, result.report());
            }
            JOptionPane.showMessageDialog(this, "Report written to " + target, "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to write report:\n" + e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
