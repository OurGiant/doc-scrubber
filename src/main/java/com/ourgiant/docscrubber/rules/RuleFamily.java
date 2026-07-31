package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RuleFamily {
    CONTENT("content"),
    STRUCTURAL("structural");

    private final String json;

    RuleFamily(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static RuleFamily fromJson(String value) {
        for (RuleFamily f : values()) {
            if (f.json.equalsIgnoreCase(value)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unknown rule family: " + value);
    }
}
