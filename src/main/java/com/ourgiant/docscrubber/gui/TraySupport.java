package com.ourgiant.docscrubber.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;

/** Wraps the platform system tray icon that lets {@link MainWindow} stay dormant instead of exiting on close. */
final class TraySupport {

    private static final Logger logger = LoggerFactory.getLogger(TraySupport.class);

    private final MainWindow window;
    private TrayIcon trayIcon;

    TraySupport(MainWindow window) {
        this.window = window;
    }

    static boolean isSupported() {
        return SystemTray.isSupported();
    }

    /** Adds the tray icon. No-op if already installed or the platform has no tray. */
    void install() {
        if (trayIcon != null || !isSupported()) {
            return;
        }

        java.net.URL iconUrl = MainWindow.class.getResource("/app-icon.png");
        Image iconImage = iconUrl != null
            ? Toolkit.getDefaultToolkit().getImage(iconUrl)
            : Toolkit.getDefaultToolkit().createImage(new byte[0]);

        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open DocScrubber");
        open.addActionListener(e -> restore());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> window.exitApp());
        menu.add(open);
        menu.addSeparator();
        menu.add(exit);

        TrayIcon icon = new TrayIcon(iconImage, "DocScrubber", menu);
        icon.setImageAutoSize(true);
        // The "default action" gesture (double-click on Windows, single-click on Linux/macOS) —
        // TrayIcon's own ActionListener is the documented cross-platform way to catch it. A
        // MouseListener's click-count is unreliable here: KDE's StatusNotifierItem tray protocol
        // doesn't forward click events in a way AWT can turn into a mouseClicked/double-click.
        icon.addActionListener(e -> restore());

        try {
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
        } catch (AWTException e) {
            logger.warn("Failed to install system tray icon", e);
        }
    }

    void uninstall() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    boolean isInstalled() {
        return trayIcon != null;
    }

    void showNotification(String caption, String text, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(caption, text, type);
        }
    }

    private void restore() {
        window.setVisible(true);
        window.setExtendedState(window.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        window.toFront();
        window.requestFocus();
    }
}
