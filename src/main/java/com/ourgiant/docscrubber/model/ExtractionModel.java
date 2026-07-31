package com.ourgiant.docscrubber.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Normalized output of a {@code DocumentParser}: every fragment of text a document contains,
 * plus honest disclosure of anything the parser could not determine reliably.
 *
 * <p>{@code limitations} exists specifically so a format like PDF — where hidden-text detection
 * is necessarily heuristic (see {@link VisibilityAttributes#isBackgroundHeuristic()}) — can say so
 * up front. The GUI and report writers must surface a non-empty limitations list unconditionally,
 * not just attach it to individual findings, so a clean score on a PDF never reads as a stronger
 * guarantee than it is.</p>
 */
public final class ExtractionModel {

    private final Path sourcePath;
    private final DocumentFormat format;
    private final List<TextFragment> fragments;
    private final List<String> limitations;

    public ExtractionModel(Path sourcePath, DocumentFormat format, List<TextFragment> fragments, List<String> limitations) {
        this.sourcePath = sourcePath;
        this.format = format;
        this.fragments = Collections.unmodifiableList(fragments);
        this.limitations = Collections.unmodifiableList(limitations);
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public DocumentFormat getFormat() {
        return format;
    }

    public List<TextFragment> getFragments() {
        return fragments;
    }

    /** Honest, format-level caveats about detection confidence (e.g. PDF heuristic background inference). */
    public List<String> getLimitations() {
        return limitations;
    }

    public boolean hasLimitations() {
        return !limitations.isEmpty();
    }
}
