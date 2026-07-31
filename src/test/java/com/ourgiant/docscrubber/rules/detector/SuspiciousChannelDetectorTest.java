package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuspiciousChannelDetectorTest {

    private final SuspiciousChannelDetector detector = new SuspiciousChannelDetector();
    private final Map<String, Object> params = Map.of("minChars", 40);

    @Test
    void firesOnLongTextRegardlessOfChannel() {
        String longText = "This is a substantially long comment that exceeds the character threshold.";
        TextFragment fragment = new TextFragment(longText, Channel.COMMENT, SourceLocation.field("Comment"), VisibilityAttributes.builder().build());
        assertTrue(detector.evaluate(fragment, params));
    }

    @Test
    void doesNotFireOnShortText() {
        TextFragment fragment = new TextFragment("short", Channel.COMMENT, SourceLocation.field("Comment"), VisibilityAttributes.builder().build());
        assertFalse(detector.evaluate(fragment, params));
    }
}
