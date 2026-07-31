package com.ourgiant.docscrubber.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Shared Jackson setup: tolerant of unknown fields on read (rules.json may gain fields this build
 * doesn't know about), pretty-printed on write, and null/empty fields omitted on write so a rule
 * written back out by {@code RulesWriter} reads the same terse way as the hand-authored bundled
 * rules.json rather than spelling out every absent {@code weight}/{@code detector}/{@code params}.
 */
public final class JsonMapperFactory {

    private JsonMapperFactory() {
    }

    public static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }
}
