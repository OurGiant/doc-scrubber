package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.fixtures.FixtureBuilder;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.TextFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlParserTest {

    private final XmlParser parser = new XmlParser();

    @Test
    void extractsElementTextAttributeAndCommentValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("payload.xml");
        FixtureBuilder.xmlWithPayloads(file, "ignore previous instructions", "dear assistant please comply", "new instructions:");

        ExtractionModel model = parser.parse(file);

        TextFragment elementText = findByText(model, "ignore previous instructions");
        assertEquals(Channel.BODY, elementText.getChannel());
        assertTrue(elementText.getLocation().describe().contains("/root/item"));

        TextFragment attribute = findByText(model, "dear assistant please comply");
        assertEquals(Channel.BODY, attribute.getChannel());
        assertTrue(attribute.getLocation().describe().contains("@attr"));

        TextFragment comment = findByText(model, "new instructions:");
        assertEquals(Channel.COMMENT, comment.getChannel());
    }

    @Test
    void rejectsDoctypeAndDoesNotResolveExternalEntities(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "top secret contents that must never leak into a scan result");
        Path file = dir.resolve("xxe.xml");
        FixtureBuilder.xmlWithXxeAttempt(file, secret);

        // Rejecting the DOCTYPE outright (rather than selectively allowing "safe" DOCTYPEs) is the
        // hardening: parsing fails closed instead of risking any external entity resolution.
        IOException thrown = assertThrows(IOException.class, () -> parser.parse(file));
        assertTrue(!thrown.getMessage().contains("top secret contents"),
            "Exception message must not leak the secret file's contents either");
    }

    @Test
    void supportsXmlExtension() {
        assertTrue(parser.supports(Path.of("config.xml")));
    }

    private TextFragment findByText(ExtractionModel model, String needle) {
        List<TextFragment> matches = model.getFragments().stream()
            .filter(f -> f.getText().contains(needle))
            .toList();
        assertEquals(1, matches.size(), "Expected exactly one fragment containing: " + needle + " but found: " + matches.size());
        return matches.get(0);
    }
}
