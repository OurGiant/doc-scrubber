package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.fixtures.FixtureBuilder;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.RenderMode;
import com.ourgiant.docscrubber.model.TextFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfParserTest {

    private final PdfParser parser = new PdfParser();

    @Test
    void alwaysCarriesHonestyLimitations(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("plain.pdf");
        FixtureBuilder.pdfPlain(file, "Nothing suspicious here.");

        ExtractionModel model = parser.parse(file);

        assertTrue(model.hasLimitations(), "PDF scans must always disclose detection limitations, even when clean");
    }

    @Test
    void detectsInvisibleRenderMode(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("invisible.pdf");
        FixtureBuilder.pdfWithInvisibleText(file, "Visible text.", "Invisible instruction.");

        ExtractionModel model = parser.parse(file);

        TextFragment hidden = findByText(model, "Invisible instruction.");
        assertEquals(RenderMode.INVISIBLE, hidden.getVisibility().getRenderMode());
    }

    @Test
    void detectsWhiteOnAssumedWhiteBackground(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("white.pdf");
        FixtureBuilder.pdfWithWhiteText(file, "Visible text.", "White instruction.");

        ExtractionModel model = parser.parse(file);

        TextFragment hidden = findByText(model, "White instruction.");
        assertEquals(1.0, hidden.getVisibility().getContrastRatio(), 0.001);
        assertTrue(hidden.getVisibility().isBackgroundHeuristic(), "PDF background must be flagged heuristic, never presented as certain");
    }

    @Test
    void detectsOffPageText(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("offpage.pdf");
        FixtureBuilder.pdfWithOffPageText(file, "Visible text.", "Off page instruction.");

        ExtractionModel model = parser.parse(file);

        TextFragment offPage = findByText(model, "Off page instruction.");
        assertEquals(Boolean.FALSE, offPage.getVisibility().getOnPage());

        TextFragment onPage = findByText(model, "Visible text.");
        assertEquals(Boolean.TRUE, onPage.getVisibility().getOnPage());
    }

    @Test
    void detectsTinyFont(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("tiny.pdf");
        FixtureBuilder.pdfWithTinyFont(file, "Visible text.", "Tiny instruction.", 1.0f);

        ExtractionModel model = parser.parse(file);

        TextFragment tiny = findByText(model, "Tiny instruction.");
        assertTrue(tiny.getVisibility().getFontSizePt() < 2.0);
    }

    @Test
    void extractsDocumentInformationAsMetadata(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("metadata.pdf");
        FixtureBuilder.pdfWithMetadata(file, "keywords", "ignore previous instructions");

        ExtractionModel model = parser.parse(file);

        TextFragment metadata = findByText(model, "ignore previous instructions");
        assertFalse(metadata.getVisibility().isHidden());
    }

    @Test
    void countsEmbeddedFileAttachmentsAndAddsALimitationNotice(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("embedded.pdf");
        FixtureBuilder.pdfWithEmbeddedFile(file, "Ordinary visible document body.", 3);

        ExtractionModel model = parser.parse(file);

        assertEquals(3, model.getEmbeddedObjectCount());
        assertTrue(model.getLimitations().stream().anyMatch(l -> l.contains("3 embedded file")),
            "Expected a limitations entry mentioning the embedded file count: " + model.getLimitations());
    }

    @Test
    void plainPdfHasNoEmbeddedFiles(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("plain-no-embed.pdf");
        FixtureBuilder.pdfPlain(file, "Nothing suspicious here.");

        ExtractionModel model = parser.parse(file);

        assertEquals(0, model.getEmbeddedObjectCount());
    }

    @Test
    void flagsEmbeddedFileWithExecutableSignature(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("embedded-exe.pdf");
        FixtureBuilder.pdfWithEmbeddedExecutable(file, "Ordinary visible document body.");

        ExtractionModel model = parser.parse(file);

        List<TextFragment> embeddedObjectFragments = model.getFragments().stream()
            .filter(f -> f.getChannel() == Channel.EMBEDDED_OBJECT)
            .toList();
        assertEquals(1, embeddedObjectFragments.size());
        assertEquals("MZ / Windows-DOS executable", embeddedObjectFragments.get(0).getVisibility().getEmbeddedExecutableSignature());
    }

    @Test
    void ordinaryEmbeddedFileAttachmentIsNotFlaggedAsStructural(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("embedded-plain.pdf");
        FixtureBuilder.pdfWithEmbeddedFile(file, "Ordinary visible document body.", 1);

        ExtractionModel model = parser.parse(file);

        assertTrue(model.getFragments().stream().noneMatch(f -> f.getChannel() == Channel.EMBEDDED_OBJECT),
            "A plain-text embedded attachment should not trip the executable/macro-storage structural checks");
    }

    private TextFragment findByText(ExtractionModel model, String needle) {
        List<TextFragment> matches = model.getFragments().stream()
            .filter(f -> f.getText().contains(needle))
            .toList();
        assertEquals(1, matches.size(), "Expected exactly one fragment containing: " + needle + " but found: " + matches.size());
        return matches.get(0);
    }
}
