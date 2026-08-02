package com.ourgiant.docscrubber.model;

import java.awt.Color;
import java.util.List;

/**
 * Everything a structural detector needs to judge a fragment without reading its text: mostly
 * whether it's actually visible to a human reader, plus a couple of non-visual structural facts
 * (embedded-object stream signals) that piggyback on the same "parser populates, detector reads"
 * shape rather than earning a parallel bag of their own. Fields are nullable/Optional-shaped on
 * purpose: a plain-text parser can only ever populate {@code hidden}, while PDF and docx populate
 * progressively more.
 *
 * <p>{@code backgroundHeuristic} matters beyond documentation: PDFBox exposes the fill color an
 * operator used, not what a renderer would composite on screen. Background color here is
 * inferred from the nearest preceding fill/rect operator or the page default, which is a real
 * but imperfect proxy. Detectors and the UI must not present contrast findings derived this way
 * with the same confidence as a docx {@code vanish} flag, which is unambiguous.</p>
 */
public final class VisibilityAttributes {

    private final Color fontColor;
    private final Color backgroundColor;
    private final boolean backgroundHeuristic;
    private final Double fontSizePt;
    private final RenderMode renderMode;
    private final boolean hidden;
    private final Boolean onPage;
    private final Double positionX;
    private final Double positionY;
    private final String embeddedExecutableSignature;
    private final List<String> embeddedMacroStorageNames;

    private VisibilityAttributes(Builder b) {
        this.fontColor = b.fontColor;
        this.backgroundColor = b.backgroundColor;
        this.backgroundHeuristic = b.backgroundHeuristic;
        this.fontSizePt = b.fontSizePt;
        this.renderMode = b.renderMode;
        this.hidden = b.hidden;
        this.onPage = b.onPage;
        this.positionX = b.positionX;
        this.positionY = b.positionY;
        this.embeddedExecutableSignature = b.embeddedExecutableSignature;
        this.embeddedMacroStorageNames = b.embeddedMacroStorageNames;
    }

    public Color getFontColor() {
        return fontColor;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    /** True when {@link #getBackgroundColor()} was inferred rather than read from an explicit source. */
    public boolean isBackgroundHeuristic() {
        return backgroundHeuristic;
    }

    public Double getFontSizePt() {
        return fontSizePt;
    }

    public RenderMode getRenderMode() {
        return renderMode;
    }

    /** Explicit hidden flag from the source format (docx run {@code vanish}, etc.), independent of render mode/contrast. */
    public boolean isHidden() {
        return hidden;
    }

    /** Null when the format has no page concept (plain text/docx body) or position wasn't tracked. */
    public Boolean getOnPage() {
        return onPage;
    }

    public Double getPositionX() {
        return positionX;
    }

    public Double getPositionY() {
        return positionY;
    }

    /** Non-null when this fragment's embedded-object stream begins with a known executable magic-byte signature, e.g. {@code "MZ / Windows-DOS executable"}. */
    public String getEmbeddedExecutableSignature() {
        return embeddedExecutableSignature;
    }

    /** Names of OLE compound-file macro-storage entries (e.g. {@code _VBA_PROJECT}) found in this fragment's embedded-object stream; empty if none or not applicable. */
    public List<String> getEmbeddedMacroStorageNames() {
        return embeddedMacroStorageNames;
    }

    /**
     * WCAG-style contrast ratio between font and background color, or null if either color is
     * unknown. Callers must check {@link #isBackgroundHeuristic()} before treating this as ground
     * truth for a PDF fragment.
     */
    public Double getContrastRatio() {
        if (fontColor == null || backgroundColor == null) {
            return null;
        }
        double l1 = relativeLuminance(fontColor);
        double l2 = relativeLuminance(backgroundColor);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(Color c) {
        double r = channelToLinear(c.getRed() / 255.0);
        double g = channelToLinear(c.getGreen() / 255.0);
        double b = channelToLinear(c.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channelToLinear(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Color fontColor;
        private Color backgroundColor;
        private boolean backgroundHeuristic;
        private Double fontSizePt;
        private RenderMode renderMode = RenderMode.UNKNOWN;
        private boolean hidden;
        private Boolean onPage;
        private Double positionX;
        private Double positionY;
        private String embeddedExecutableSignature;
        private List<String> embeddedMacroStorageNames = List.of();

        public Builder fontColor(Color v) {
            this.fontColor = v;
            return this;
        }

        public Builder backgroundColor(Color v, boolean heuristic) {
            this.backgroundColor = v;
            this.backgroundHeuristic = heuristic;
            return this;
        }

        public Builder fontSizePt(Double v) {
            this.fontSizePt = v;
            return this;
        }

        public Builder renderMode(RenderMode v) {
            this.renderMode = v;
            return this;
        }

        public Builder hidden(boolean v) {
            this.hidden = v;
            return this;
        }

        public Builder onPage(Boolean v) {
            this.onPage = v;
            return this;
        }

        public Builder position(Double x, Double y) {
            this.positionX = x;
            this.positionY = y;
            return this;
        }

        public Builder embeddedExecutableSignature(String v) {
            this.embeddedExecutableSignature = v;
            return this;
        }

        public Builder embeddedMacroStorageNames(List<String> v) {
            this.embeddedMacroStorageNames = v == null ? List.of() : List.copyOf(v);
            return this;
        }

        public VisibilityAttributes build() {
            return new VisibilityAttributes(this);
        }
    }
}
