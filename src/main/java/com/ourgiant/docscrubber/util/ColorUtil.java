package com.ourgiant.docscrubber.util;

import java.awt.Color;

public final class ColorUtil {

    private ColorUtil() {
    }

    /** Parses a bare 6-digit hex RGB string (docx/PDF style, no leading '#'). Returns {@code fallback} for null/"auto"/malformed input. */
    public static Color parseHex(String hex, Color fallback) {
        if (hex == null || hex.isBlank() || "auto".equalsIgnoreCase(hex)) {
            return fallback;
        }
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        if (cleaned.length() != 6) {
            return fallback;
        }
        try {
            int r = Integer.parseInt(cleaned.substring(0, 2), 16);
            int g = Integer.parseInt(cleaned.substring(2, 4), 16);
            int b = Integer.parseInt(cleaned.substring(4, 6), 16);
            return new Color(r, g, b);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
