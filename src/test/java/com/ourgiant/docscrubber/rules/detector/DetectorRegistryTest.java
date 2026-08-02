package com.ourgiant.docscrubber.rules.detector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorRegistryTest {

    private final DetectorRegistry registry = new DetectorRegistry();

    @Test
    void knowsAllSeedRulesetDetectorIds() {
        for (String id : new String[] {
            "lowContrastText", "tinyFont", "hiddenRun", "invisibleRenderMode",
            "offPageText", "suspiciousChannel", "overlappedText",
            "embeddedExecutableSignature", "embeddedMacroStorage"
        }) {
            assertTrue(registry.isKnown(id), "Expected detector registered: " + id);
            assertTrue(registry.lookup(id).isPresent());
        }
    }

    @Test
    void reportsUnknownIdWithoutThrowing() {
        assertFalse(registry.isKnown("notARealDetector"));
        assertTrue(registry.lookup("notARealDetector").isEmpty());
    }
}
