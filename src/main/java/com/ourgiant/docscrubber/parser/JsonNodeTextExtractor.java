package com.ourgiant.docscrubber.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Walks a Jackson {@link JsonNode} tree (shared by {@link JsonParser} and {@link YamlParser}, which
 * both parse into this same model) and extracts every string leaf value as a {@link Channel#BODY}
 * fragment. Object/array keys and structure are not extracted — a value like a description or name
 * field is where a payload would hide, not the key naming it.
 */
final class JsonNodeTextExtractor {

    private JsonNodeTextExtractor() {
    }

    static List<TextFragment> extract(JsonNode root) {
        List<TextFragment> fragments = new ArrayList<>();
        walk(root, "$", fragments);
        return fragments;
    }

    private static void walk(JsonNode node, String path, List<TextFragment> out) {
        if (node.isTextual()) {
            String text = node.asText();
            if (!text.isBlank()) {
                out.add(new TextFragment(text, Channel.BODY, SourceLocation.field(path), VisibilityAttributes.builder().build()));
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                walk(entry.getValue(), path + "." + entry.getKey(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), path + "[" + i + "]", out);
            }
        }
    }
}
