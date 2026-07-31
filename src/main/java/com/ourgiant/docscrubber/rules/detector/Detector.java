package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/** A built-in structural check, parameterized from a rule's JSON {@code params} object. New detectors require code; their thresholds/enablement do not. */
public interface Detector {

    String id();

    boolean evaluate(TextFragment fragment, Map<String, Object> params);
}
