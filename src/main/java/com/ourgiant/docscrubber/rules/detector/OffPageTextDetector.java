package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/** Fires when text was positioned outside the page's crop box (negative coordinates, or beyond its width/height). PDF only — {@code onPage} is null for other formats and simply never matches. */
public final class OffPageTextDetector implements Detector {

    @Override
    public String id() {
        return "offPageText";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        return Boolean.FALSE.equals(fragment.getVisibility().getOnPage());
    }
}
