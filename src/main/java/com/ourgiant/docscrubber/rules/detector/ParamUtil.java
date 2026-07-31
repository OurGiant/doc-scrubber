package com.ourgiant.docscrubber.rules.detector;

import java.util.Map;

/** Small helpers for reading typed values out of a rule's untyped JSON {@code params} map. */
final class ParamUtil {

    private ParamUtil() {
    }

    static double getDouble(Map<String, Object> params, String key, double defaultValue) {
        Object v = params.get(key);
        return v instanceof Number n ? n.doubleValue() : defaultValue;
    }

    static int getInt(Map<String, Object> params, String key, int defaultValue) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : defaultValue;
    }
}
