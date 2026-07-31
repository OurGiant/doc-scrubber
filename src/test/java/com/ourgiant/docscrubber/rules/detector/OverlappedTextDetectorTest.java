package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OverlappedTextDetectorTest {

    @Test
    void isAlwaysFalseByDesign() {
        // Documents the intentional no-op: overlap/z-order detection is not implemented (see class
        // javadoc), and the seed rule referencing it ships with enabled: false. This test exists so
        // a future accidental "return true" doesn't silently start flagging documents.
        OverlappedTextDetector detector = new OverlappedTextDetector();
        TextFragment fragment = new TextFragment("text", Channel.BODY, SourceLocation.page(0), VisibilityAttributes.builder().build());
        assertFalse(detector.evaluate(fragment, Map.of()));
    }
}
