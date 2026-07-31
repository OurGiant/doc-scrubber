package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/** Fires on any substantive run of text ({@code minChars} or more), regardless of styling. Meant to be paired with a rule's {@code channels} restriction (metadata, comment, alt-text, tracked-change, ...) — the channel filter does the real work, this just gates on length. */
public final class SuspiciousChannelDetector implements Detector {

    @Override
    public String id() {
        return "suspiciousChannel";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        int minChars = ParamUtil.getInt(params, "minChars", 40);
        return fragment.getText().trim().length() >= minChars;
    }
}
