package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.model.ExtractionModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainTextParserTest {

    private final PlainTextParser parser = new PlainTextParser();

    @Test
    void splitsBlankLineDelimitedParagraphs(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.txt");
        Files.writeString(file, "First paragraph line one.\nFirst paragraph line two.\n\nSecond paragraph.\n");

        ExtractionModel model = parser.parse(file);

        assertEquals(DocumentFormat.PLAIN_TEXT, model.getFormat());
        assertEquals(2, model.getFragments().size());
        assertTrue(model.getFragments().get(0).getText().contains("First paragraph line one."));
        assertTrue(model.getFragments().get(1).getText().contains("Second paragraph."));
        assertTrue(model.getLimitations().isEmpty());
        model.getFragments().forEach(f -> assertEquals(Channel.BODY, f.getChannel()));
    }

    @Test
    void detectsMarkdownExtension(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.md");
        Files.writeString(file, "# Heading\n");

        ExtractionModel model = parser.parse(file);

        assertEquals(DocumentFormat.MARKDOWN, model.getFormat());
    }

    @Test
    void supportsOnlyTextAndMarkdown() {
        assertTrue(parser.supports(Path.of("a.txt")));
        assertTrue(parser.supports(Path.of("a.md")));
        assertFalse(parser.supports(Path.of("a.docx")));
        assertFalse(parser.supports(Path.of("a.pdf")));
    }

    @Test
    void supportsSourceAndDataTextFormats() {
        assertTrue(parser.supports(Path.of("a.ts")));
        assertTrue(parser.supports(Path.of("a.tsx")));
        assertTrue(parser.supports(Path.of("a.js")));
        assertTrue(parser.supports(Path.of("a.jsx")));
        assertTrue(parser.supports(Path.of("a.py")));
        assertTrue(parser.supports(Path.of("a.html")));
        assertTrue(parser.supports(Path.of("a.htm")));
        assertTrue(parser.supports(Path.of("a.csv")));
    }

    @Test
    void treatsSourceAndDataTextFormatsAsPlainText(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.py");
        Files.writeString(file, "def greet():\n    print('hi')\n");

        ExtractionModel model = parser.parse(file);

        assertEquals(DocumentFormat.PLAIN_TEXT, model.getFormat());
        assertEquals(1, model.getFragments().size());
        assertTrue(model.getFragments().get(0).getText().contains("def greet():"));
    }
}
