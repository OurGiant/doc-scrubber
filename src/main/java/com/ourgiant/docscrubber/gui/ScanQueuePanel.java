package com.ourgiant.docscrubber.gui;

import com.ourgiant.docscrubber.parser.ParserRegistry;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/** Drag-and-drop + file-chooser queue of documents to scan. Scanning itself and status display are driven by {@link MainWindow}; this panel only manages membership and selection. */
final class ScanQueuePanel extends JPanel {

    private final ParserRegistry parserRegistry;
    private final DefaultListModel<Path> listModel = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(listModel);

    private Consumer<Path> onFileAdded = p -> {
    };
    private Consumer<Path> onSelectionChanged = p -> {
    };
    private Function<Path, String> statusResolver = p -> "scanning...";

    ScanQueuePanel(ParserRegistry parserRegistry) {
        super(new BorderLayout());
        this.parserRegistry = parserRegistry;

        list.setCellRenderer(new StatusCellRenderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelectionChanged.accept(list.getSelectedValue());
            }
        });

        JLabel dropHint = new JLabel("Drag files or folders here, or use Add Files/Folder...", SwingConstants.CENTER);
        dropHint.setForeground(Color.GRAY);
        dropHint.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JButton addButton = new JButton("Add Files/Folder...");
        addButton.addActionListener(e -> chooseFiles());
        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> removeSelected());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(addButton);
        buttons.add(removeButton);

        add(dropHint, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        new DropTarget(list, DnDConstants.ACTION_COPY, new DropHandler());
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropHandler());
    }

    void setOnFileAdded(Consumer<Path> listener) {
        this.onFileAdded = listener;
    }

    void setOnSelectionChanged(Consumer<Path> listener) {
        this.onSelectionChanged = listener;
    }

    void setStatusResolver(Function<Path, String> resolver) {
        this.statusResolver = resolver;
    }

    void refreshStatus() {
        list.repaint();
    }

    void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new FileNameExtensionFilter("Supported documents (txt, md, docx, pdf, json, yaml, xml)",
            "txt", "md", "markdown", "docx", "pdf", "json", "yaml", "yml", "xml"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                addPath(f.toPath());
            }
        }
    }

    private void removeSelected() {
        for (Path p : list.getSelectedValuesList()) {
            listModel.removeElement(p);
        }
    }

    /** Dispatches a chosen or dropped path: a single file goes through the usual supported-type check (with a warning dialog if not), a folder is walked recursively in the background and only its supported files are added silently — a folder can easily contain hundreds of irrelevant files, and a per-file warning popup for each would be unusable. */
    private void addPath(Path path) {
        if (Files.isDirectory(path)) {
            addFolderAsync(path);
        } else {
            addFile(path);
        }
    }

    private void addFile(Path path) {
        if (!parserRegistry.isSupported(path)) {
            JOptionPane.showMessageDialog(this, "Unsupported file type: " + path.getFileName(), "Unsupported file", JOptionPane.WARNING_MESSAGE);
            return;
        }
        addSupportedFile(path);
    }

    private void addFolderAsync(Path dir) {
        new SwingWorker<List<Path>, Void>() {
            @Override
            protected List<Path> doInBackground() throws IOException {
                try (Stream<Path> walk = Files.walk(dir)) {
                    return walk.filter(Files::isRegularFile)
                        .filter(parserRegistry::isSupported)
                        .sorted(Comparator.naturalOrder())
                        .toList();
                }
            }

            @Override
            protected void done() {
                try {
                    get().forEach(ScanQueuePanel.this::addSupportedFile);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(ScanQueuePanel.this,
                        "Could not read folder \"" + dir.getFileName() + "\": " + cause.getMessage(),
                        "Folder read error", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void addSupportedFile(Path path) {
        if (listModel.contains(path)) {
            list.setSelectedValue(path, true);
            return;
        }
        listModel.addElement(path);
        list.setSelectedValue(path, true);
        onFileAdded.accept(path);
    }

    private final class DropHandler extends java.awt.dnd.DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent event) {
            event.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = event.getTransferable();
            try {
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        addPath(f.toPath());
                    }
                    event.dropComplete(true);
                } else {
                    event.dropComplete(false);
                }
            } catch (UnsupportedFlavorException | IOException e) {
                event.dropComplete(false);
            }
        }
    }

    private final class StatusCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Path path && c instanceof JLabel label) {
                String status = statusResolver.apply(path);
                label.setText(path.getFileName() + (status == null || status.isBlank() ? "" : " — " + status));
            }
            return c;
        }
    }
}
