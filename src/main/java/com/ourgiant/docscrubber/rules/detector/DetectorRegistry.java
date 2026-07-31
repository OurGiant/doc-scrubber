package com.ourgiant.docscrubber.rules.detector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class DetectorRegistry {

    private final Map<String, Detector> detectors = new HashMap<>();

    public DetectorRegistry() {
        register(new LowContrastTextDetector());
        register(new TinyFontDetector());
        register(new HiddenRunDetector());
        register(new InvisibleRenderModeDetector());
        register(new OffPageTextDetector());
        register(new SuspiciousChannelDetector());
        register(new OverlappedTextDetector());
    }

    private void register(Detector detector) {
        detectors.put(detector.id(), detector);
    }

    public Optional<Detector> lookup(String id) {
        return Optional.ofNullable(detectors.get(id));
    }

    public boolean isKnown(String id) {
        return detectors.containsKey(id);
    }
}
