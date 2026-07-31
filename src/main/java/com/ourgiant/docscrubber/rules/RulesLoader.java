package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.docscrubber.util.JsonMapperFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pure JSON parsing of rules.json into a {@link RuleSet}. Schema/semantic validation is a separate step — see {@link RulesValidator}. */
public final class RulesLoader {

    public static final String DEFAULT_RULES_RESOURCE = "/rules/rules.json";

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();

    public RuleSet loadDefault() throws IOException {
        try (InputStream in = RulesLoader.class.getResourceAsStream(DEFAULT_RULES_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled default rules resource not found: " + DEFAULT_RULES_RESOURCE);
            }
            return mapper.readValue(in, RuleSet.class);
        }
    }

    public RuleSet load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return mapper.readValue(in, RuleSet.class);
        }
    }
}
