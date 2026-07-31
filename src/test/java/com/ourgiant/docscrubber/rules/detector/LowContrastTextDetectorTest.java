package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowContrastTextDetectorTest {

    private final LowContrastTextDetector detector = new LowContrastTextDetector();
    private final Map<String, Object> defaultParams = Map.of("maxContrastRatio", 1.3, "minChars", 8);

    @Test
    void firesOnWhiteOnWhite() {
        TextFragment fragment = fragment("this is a hidden instruction", Color.WHITE, Color.WHITE);
        assertTrue(detector.evaluate(fragment, defaultParams));
    }

    @Test
    void doesNotFireOnNormalContrast() {
        TextFragment fragment = fragment("this is normal visible text", Color.BLACK, Color.WHITE);
        assertFalse(detector.evaluate(fragment, defaultParams));
    }

    @Test
    void doesNotFireBelowMinChars() {
        TextFragment fragment = fragment("hi", Color.WHITE, Color.WHITE);
        assertFalse(detector.evaluate(fragment, defaultParams));
    }

    private TextFragment fragment(String text, Color font, Color background) {
        VisibilityAttributes visibility = VisibilityAttributes.builder()
            .fontColor(font)
            .backgroundColor(background, false)
            .build();
        return new TextFragment(text, Channel.BODY, SourceLocation.page(0), visibility);
    }
}
