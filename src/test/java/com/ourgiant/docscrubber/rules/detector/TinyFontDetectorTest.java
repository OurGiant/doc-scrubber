package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TinyFontDetectorTest {

    private final TinyFontDetector detector = new TinyFontDetector();
    private final Map<String, Object> params = Map.of("maxPt", 2.0);

    @Test
    void firesBelowThreshold() {
        assertTrue(detector.evaluate(fragment(1.0), params));
    }

    @Test
    void doesNotFireAtNormalSize() {
        assertFalse(detector.evaluate(fragment(12.0), params));
    }

    @Test
    void doesNotFireWhenSizeUnknown() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
        assertFalse(detector.evaluate(fragment, params));
    }

    private TextFragment fragment(double sizePt) {
        VisibilityAttributes visibility = VisibilityAttributes.builder().fontSizePt(sizePt).build();
        return new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
    }
}
