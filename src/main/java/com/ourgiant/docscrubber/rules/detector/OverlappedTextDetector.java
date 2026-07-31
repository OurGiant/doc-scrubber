package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/**
 * Intentionally a no-op for now. Reliable detection of text visually obscured by a later-painted
 * image or shape requires tracking paint order (text vs. image draw operations) through the PDF
 * content stream. A half-working version of that risks flagging watermarks, decorative background
 * images, and transparent overlays as "hidden text" — a worse outcome than not detecting this
 * pattern at all, per product decision (favor no false positives / no artificially low scores over
 * broader but noisier coverage).
 *
 * <p>Registered here (rather than left out of {@link DetectorRegistry}) so a rule referencing
 * {@code overlappedText} loads as a normal, silently-false rule instead of triggering the
 * "unknown detector id" validation warning. The corresponding seed rule ships with
 * {@code enabled: false} and says so in its description.</p>
 */
public final class OverlappedTextDetector implements Detector {

    @Override
    public String id() {
        return "overlappedText";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        return false;
    }
}
