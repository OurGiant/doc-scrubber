package com.ourgiant.docscrubber.util;

/**
 * Prepares matched text for display as finding evidence: collapsed, length-capped, and with
 * invisible/control characters made visible. Without the visibility pass, evidence for a
 * zero-width-character finding would render as an empty or misleading string — exactly the kind
 * of "looks clean" result this tool exists to prevent.
 */
public final class EvidenceUtil {

    private static final int DEFAULT_MAX_LENGTH = 200;

    private EvidenceUtil() {
    }

    public static String prepare(String text) {
        return prepare(text, DEFAULT_MAX_LENGTH);
    }

    public static String prepare(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String collapsed = text.strip().replaceAll("\\s+", " ");
        StringBuilder visible = new StringBuilder(collapsed.length());
        collapsed.codePoints().forEach(cp -> visible.append(visibleMarkerOrChar(cp)));
        String result = visible.toString();
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength) + "…";
        }
        return result;
    }

    private static String visibleMarkerOrChar(int cp) {
        String name = switch (cp) {
            case 0x200B -> "[ZWSP]";
            case 0x200C -> "[ZWNJ]";
            case 0x200D -> "[ZWJ]";
            case 0x2060 -> "[WJ]";
            case 0xFEFF -> "[BOM]";
            case 0x202A -> "[LRE]";
            case 0x202B -> "[RLE]";
            case 0x202C -> "[PDF]";
            case 0x202D -> "[LRO]";
            case 0x202E -> "[RLO]";
            default -> null;
        };
        if (name != null) {
            return name;
        }
        if (cp >= 0x2066 && cp <= 0x2069) {
            return "[BIDI]";
        }
        if (cp >= 0xE0000 && cp <= 0xE007F) {
            return "[TAG]";
        }
        if (Character.isISOControl(cp) && cp != '\n' && cp != '\t' && cp != ' ') {
            return "[CTRL]";
        }
        return new String(Character.toChars(cp));
    }
}
