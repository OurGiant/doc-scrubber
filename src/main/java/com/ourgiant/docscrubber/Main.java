package com.ourgiant.docscrubber;

import com.ourgiant.docscrubber.gui.MainWindow;

import javax.swing.*;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            if (!ThemeManager.applyTheme("Flat Light")) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // Fall through to whatever the platform default is.
                }
            }

            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
