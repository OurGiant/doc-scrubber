package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.rules.ValidationResult;

import javax.swing.*;
import java.awt.*;

/** Shared errors/warnings dialog used by both the ad hoc "Validate Rules" menu action and the Rules window's save-time validation. */
final class ValidationDialogs {

    private ValidationDialogs() {
    }

    static void show(Component parent, String title, ValidationResult result) {
        if (result.isValid() && result.getWarnings().isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No problems found.", title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (!result.getErrors().isEmpty()) {
            sb.append("Errors:\n");
            for (String error : result.getErrors()) {
                sb.append("  • ").append(error).append('\n');
            }
        }
        if (!result.getWarnings().isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Warnings:\n");
            for (String warning : result.getWarnings()) {
                sb.append("  • ").append(warning).append('\n');
            }
        }

        JTextArea area = new JTextArea(sb.toString().stripTrailing());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(new Dimension(560, 320));

        int messageType = result.isValid() ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE;
        JOptionPane.showMessageDialog(parent, scrollPane, title, messageType);
    }
}
