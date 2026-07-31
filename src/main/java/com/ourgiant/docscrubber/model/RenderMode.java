package com.ourgiant.docscrubber.model;

/**
 * PDF text rendering mode (PDF spec 9.3.3, the "Tr" operator), extended with UNKNOWN for
 * formats where the concept doesn't apply (plain text, docx).
 */
public enum RenderMode {
    FILL,
    STROKE,
    FILL_STROKE,
    INVISIBLE,
    FILL_CLIP,
    STROKE_CLIP,
    FILL_STROKE_CLIP,
    CLIP,
    UNKNOWN;

    public static RenderMode fromPdfTr(int tr) {
        return switch (tr) {
            case 0 -> FILL;
            case 1 -> STROKE;
            case 2 -> FILL_STROKE;
            case 3 -> INVISIBLE;
            case 4 -> FILL_CLIP;
            case 5 -> STROKE_CLIP;
            case 6 -> FILL_STROKE_CLIP;
            case 7 -> CLIP;
            default -> UNKNOWN;
        };
    }
}
