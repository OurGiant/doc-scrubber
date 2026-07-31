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

class HiddenRunDetectorTest {

    private final HiddenRunDetector detector = new HiddenRunDetector();

    @Test
    void firesOnDocxStyleHiddenFlag() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().hidden(true).build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.paragraphRun(0, 0), visibility);
        assertTrue(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireOnVisibleRun() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().hidden(false).build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.paragraphRun(0, 0), visibility);
        assertFalse(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotDoubleCountPdfInvisibleRenderMode() {
        // PDF Tr3 sets hidden=true AND renderMode=INVISIBLE; that combination belongs to
        // InvisibleRenderModeDetector, not this one, so the two detectors never both fire on the same fragment.
        VisibilityAttributes visibility = VisibilityAttributes.builder().hidden(true).renderMode(RenderMode.INVISIBLE).build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
        assertFalse(detector.evaluate(fragment, Map.of()));
    }
}
