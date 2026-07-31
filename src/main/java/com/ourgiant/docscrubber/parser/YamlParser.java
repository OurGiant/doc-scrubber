package com.ourgiant.docscrubber.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts every string value in a .yaml/.yml file (same neutral-visibility approach as
 * {@link JsonParser}, sharing {@link JsonNodeTextExtractor} since both parse into the same Jackson
 * tree model), plus standalone {@code #} comment lines as {@link Channel#COMMENT} fragments — a
 * channel a YAML-consuming tool's parser never sees, since comments aren't part of the parsed model
 * at all.
 *
 * <p>Only whole-line comments (the line's first non-whitespace character is {@code #}) are
 * detected. An inline trailing comment after a value (e.g. {@code key: value # comment}) is not
 * reliably distinguishable from a {@code #} inside a quoted scalar without a real YAML tokenizer,
 * so that case is not covered — a deliberate, documented scope limit, not an oversight.</p>
 */
public final class YamlParser implements DocumentParser {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Override
    public boolean supports(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    @Override
    public ExtractionModel parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        JsonNode root = mapper.readTree(String.join("\n", lines));

        List<TextFragment> fragments = new ArrayList<>(JsonNodeTextExtractor.extract(root));
        extractCommentLines(lines, fragments);
        return new ExtractionModel(path, DocumentFormat.YAML, fragments, List.of());
    }

    private void extractCommentLines(List<String> lines, List<TextFragment> out) {
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).stripLeading();
            if (trimmed.startsWith("#")) {
                String text = trimmed.substring(1).strip();
                if (!text.isEmpty()) {
                    out.add(new TextFragment(text, Channel.COMMENT, SourceLocation.line(i), VisibilityAttributes.builder().build()));
                }
            }
        }
    }
}
