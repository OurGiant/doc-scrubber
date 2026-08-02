package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedExecutableSignatureDetectorTest {

    private final EmbeddedExecutableSignatureDetector detector = new EmbeddedExecutableSignatureDetector();

    @Test
    void firesWhenSignaturePresentOnEmbeddedObjectChannel() {
        TextFragment fragment = fragment(Channel.EMBEDDED_OBJECT, "MZ / Windows-DOS executable");
        assertTrue(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireWithoutASignature() {
        TextFragment fragment = fragment(Channel.EMBEDDED_OBJECT, null);
        assertFalse(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireOutsideEmbeddedObjectChannel() {
        TextFragment fragment = fragment(Channel.BODY, "MZ / Windows-DOS executable");
        assertFalse(detector.evaluate(fragment, Map.of()));
    }

    private TextFragment fragment(Channel channel, String executableSignature) {
        VisibilityAttributes visibility = VisibilityAttributes.builder()
            .embeddedExecutableSignature(executableSignature)
            .build();
        return new TextFragment("Embedded object \"x.bin\"", channel, SourceLocation.field("Embedded object: x.bin"), visibility);
    }
}
