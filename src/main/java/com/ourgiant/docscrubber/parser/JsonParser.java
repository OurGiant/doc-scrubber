package com.ourgiant.docscrubber.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.TextFragment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Extracts every string value in a .json file as a {@link com.ourgiant.docscrubber.model.Channel#BODY}
 * fragment. There is no hidden-text or comment concept in JSON, so every fragment gets the same
 * neutral visibility (structural detectors will simply never fire on these documents) — the same
 * pattern {@link PlainTextParser} uses for the same reason.
 */
public final class JsonParser implements DocumentParser {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    @Override
    public ExtractionModel parse(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        List<TextFragment> fragments = JsonNodeTextExtractor.extract(root);
        return new ExtractionModel(path, DocumentFormat.JSON, fragments, List.of());
    }
}
