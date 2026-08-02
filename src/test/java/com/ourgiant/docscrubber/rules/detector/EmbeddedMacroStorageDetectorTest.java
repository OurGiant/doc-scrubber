package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedMacroStorageDetectorTest {

    private final EmbeddedMacroStorageDetector detector = new EmbeddedMacroStorageDetector();

    @Test
    void firesWhenMacroStorageNamesPresentOnEmbeddedObjectChannel() {
        TextFragment fragment = fragment(Channel.EMBEDDED_OBJECT, List.of("_VBA_PROJECT"));
        assertTrue(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireWithoutMacroStorageNames() {
        TextFragment fragment = fragment(Channel.EMBEDDED_OBJECT, List.of());
        assertFalse(detector.evaluate(fragment, Map.of()));
    }

    @Test
    void doesNotFireOutsideEmbeddedObjectChannel() {
        TextFragment fragment = fragment(Channel.BODY, List.of("_VBA_PROJECT"));
        assertFalse(detector.evaluate(fragment, Map.of()));
    }

    private TextFragment fragment(Channel channel, List<String> macroStorageNames) {
        VisibilityAttributes visibility = VisibilityAttributes.builder()
            .embeddedMacroStorageNames(macroStorageNames)
            .build();
        return new TextFragment("Embedded object \"x.bin\"", channel, SourceLocation.field("Embedded object: x.bin"), visibility);
    }
}
