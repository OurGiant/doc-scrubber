package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.RenderMode;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;

import java.util.Map;

/** Fires on an explicit hidden flag from a format with no PDF-style render mode (docx {@code w:vanish}). Discriminated from {@link InvisibleRenderModeDetector} by render mode so the two never double-count the same fragment. */
public final class HiddenRunDetector implements Detector {

    @Override
    public String id() {
        return "hiddenRun";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        VisibilityAttributes v = fragment.getVisibility();
        return v.isHidden() && v.getRenderMode() == RenderMode.UNKNOWN;
    }
}
