package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.RenderMode;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvisibleRenderModeDetectorTest {

    private final InvisibleRenderModeDetector detector = new InvisibleRenderModeDetector();

    @Test
    void firesOnRenderMode3() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().renderMode(RenderMode.INVISIBLE).build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
        assertTrue(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireOnNormalFillMode() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().renderMode(RenderMode.FILL).build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
        assertFalse(detector.evaluate(fragment, Map.of()));
    }
}
