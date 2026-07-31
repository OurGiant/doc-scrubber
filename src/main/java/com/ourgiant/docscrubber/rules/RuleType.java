package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RuleType {
    REGEX("regex"),
    KEYWORD_LIST("keywordList"),
    UNICODE_CLASS("unicodeClass"),
    DETECTOR("detector");

    private final String json;

    RuleType(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static RuleType fromJson(String value) {
        for (RuleType t : values()) {
            if (t.json.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown rule type: " + value);
    }
}
