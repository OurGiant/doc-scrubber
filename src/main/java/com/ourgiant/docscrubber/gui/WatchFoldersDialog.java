package com.ourgiant.docscrubber.gui;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lets the user maintain the list of directories the app watches for new files to auto-scan.
 * Changes apply immediately (each Add/Remove fires {@code onChange}) rather than needing a
 * separate Save step, matching this app's other lightweight settings surfaces.
 */
final class WatchFoldersDialog extends JDialog {

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);
    private final Consumer<List<String>> onChange;

    WatchFoldersDialog(Frame parent, List<String> initialDirectories, Consumer<List<String>> onChange) {
        super(parent, "Watch Folders", false);
        this.onChange = onChange;

        for (String dir : initialDirectories) {
            listModel.addElement(dir);
        }

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel hint = new JLabel("New supported files added to these folders are scanned automatically, with the result sent as a tray notification.");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(hint, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(480, 220));
        add(scrollPane, BorderLayout.CENTER);

        JButton addButton = new JButton("Add Folder...");
        addButton.addActionListener(e -> addFolder());
        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> removeSelected());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }

    private void addFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String path = chooser.getSelectedFile().toPath().toAbsolutePath().toString();
        if (!listModel.contains(path)) {
            listModel.addElement(path);
            fireChange();
        }
    }

    private void removeSelected() {
        for (String selected : list.getSelectedValuesList()) {
            listModel.removeElement(selected);
        }
        fireChange();
    }

    private void fireChange() {
        onChange.accept(Collections.list(listModel.elements()));
    }
}
