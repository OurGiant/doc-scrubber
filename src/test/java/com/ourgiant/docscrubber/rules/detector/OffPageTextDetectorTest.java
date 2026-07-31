package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffPageTextDetectorTest {

    private final OffPageTextDetector detector = new OffPageTextDetector();

    @Test
    void firesWhenOffPage() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().onPage(false).build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
        assertTrue(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireWhenOnPage() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().onPage(true).build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
        assertFalse(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireWhenUnknown() {
        VisibilityAttributes visibility = VisibilityAttributes.builder().build();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), visibility);
        assertFalse(detector.evaluate(fragment, Map.of()));
    }
}
