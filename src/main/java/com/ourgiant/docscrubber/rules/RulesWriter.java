package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.docscrubber.util.JsonMapperFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serializes a {@link RuleSet} back to the {@code rules.json} shape read by {@link RulesLoader} — the write side of round-tripping edits made in the Rules window. */
public final class RulesWriter {

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();

    public void write(Path path, RuleSet ruleSet) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            mapper.writeValue(writer, ruleSet);
        }
    }
}
