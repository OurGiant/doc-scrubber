package com.ourgiant.docscrubber;

import java.util.prefs.Preferences;

/** Local app state. Currently just tracks which update version the silent startup check has already notified about. */
public class AppPreferences {

    private static final String KEY_LAST_NOTIFIED_UPDATE_VERSION = "lastNotifiedUpdateVersion";
    private static final String KEY_MINIMIZE_TO_TRAY = "minimizeToTrayEnabled";

    private final Preferences prefs;

    public AppPreferences() {
        this.prefs = Preferences.userNodeForPackage(AppPreferences.class);
    }

    /**
     * The version the silent startup update check last auto-opened the About box for, so it
     * doesn't nag on every single launch while a known update sits unapplied — once per new
     * version, not once per launch. Empty string if never notified.
     */
    public String getLastNotifiedUpdateVersion() {
        return prefs.get(KEY_LAST_NOTIFIED_UPDATE_VERSION, "");
    }

    public void setLastNotifiedUpdateVersion(String version) {
        prefs.put(KEY_LAST_NOTIFIED_UPDATE_VERSION, version);
    }

    /** Whether closing the main window hides it to the system tray instead of exiting. Defaults on when the platform tray is available. */
    public boolean isMinimizeToTrayEnabled() {
        return prefs.getBoolean(KEY_MINIMIZE_TO_TRAY, true);
    }

    public void setMinimizeToTrayEnabled(boolean enabled) {
        prefs.putBoolean(KEY_MINIMIZE_TO_TRAY, enabled);
    }
}
