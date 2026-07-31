package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.RenderMode;
import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/** Fires on PDF text render mode 3 ("neither fill nor stroke") — an unambiguous, non-heuristic signal that the text was never meant to be seen. */
public final class InvisibleRenderModeDetector implements Detector {

    @Override
    public String id() {
        return "invisibleRenderMode";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        return fragment.getVisibility().getRenderMode() == RenderMode.INVISIBLE;
    }
}
