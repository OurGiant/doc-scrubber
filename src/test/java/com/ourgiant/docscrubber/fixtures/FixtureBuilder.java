package com.ourgiant.docscrubber.fixtures;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Programmatically builds malicious/benign test documents so fixtures never need to be
 * hand-crafted binaries committed to the repo. Each method writes directly to {@code target}.
 */
public final class FixtureBuilder {

    private FixtureBuilder() {
    }

    // ---------------------------------------------------------------- plain text / markdown

    public static void plainText(Path target, String text) throws IOException {
        Files.writeString(target, text, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- docx

    public static void docxPlain(Path target, String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.createRun().setText(text);
            save(doc, target);
        }
    }

    /** Visible text plus a same-paragraph run colored to match the (assumed white) page background. */
    public static void docxWithWhiteText(Path target, String visibleText, String hiddenText) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.createRun().setText(visibleText + " ");
            XWPFRun hidden = paragraph.createRun();
            hidden.setText(hiddenText);
            hidden.setColor("FFFFFF");
            save(doc, target);
        }
    }

    /** Visible text plus a run marked {@code w:vanish} (Word's "hidden text" formatting toggle). */
    public static void docxWithVanishRun(Path target, String visibleText, String hiddenText) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.createRun().setText(visibleText + " ");
            XWPFRun hidden = paragraph.createRun();
            hidden.setText(hiddenText);
            hidden.setVanish(true);
            save(doc, target);
        }
    }

    /** Visible text with a codepoint (e.g. U+200B zero-width space) repeated {@code count} times spliced in. */
    public static void docxWithUnicodeSmuggling(Path target, String visibleText, int codePoint, int count) throws IOException {
        StringBuilder smuggled = new StringBuilder();
        for (int i = 0; i < count; i++) {
            smuggled.appendCodePoint(codePoint);
        }
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.createRun().setText(visibleText + smuggled);
            save(doc, target);
        }
    }

    /** Only a default header/footer — no first-page/even-page variant. This is the common case (most real-world docx files) and is what regressed in DocxParser when it used List.of(...) on the possibly-null getFirstPageHeader()/getEvenPageHeader(). */
    public static void docxWithDefaultHeaderFooterOnly(Path target, String bodyText, String headerText, String footerText) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText(bodyText);
            XWPFHeaderFooterPolicy policy = doc.createHeaderFooterPolicy();
            policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT).createParagraph().createRun().setText(headerText);
            policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT).createParagraph().createRun().setText(footerText);
            save(doc, target);
        }
    }

    public static void docxWithCustomProperty(Path target, String propertyName, String payload) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Ordinary visible document body.");
            doc.getProperties().getCustomProperties().addProperty(propertyName, payload);
            save(doc, target);
        }
    }

    public static void docxWithCoreProperty(Path target, String field, String payload) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Ordinary visible document body.");
            var core = doc.getProperties().getCoreProperties();
            switch (field.toLowerCase()) {
                case "title" -> core.setTitle(payload);
                case "subject" -> core.setSubjectProperty(payload);
                case "description" -> core.setDescription(payload);
                case "keywords" -> core.setKeywords(payload);
                default -> throw new IllegalArgumentException("Unsupported core property: " + field);
            }
            save(doc, target);
        }
    }

    /**
     * Visible body text plus {@code count} embedded OLE-object package parts, wired up via a raw OPC
     * relationship of type ".../relationships/oleObject" — POI's XWPF usermodel API has no
     * convenience method for creating these (only for reading them back via
     * {@code getAllEmbeddedParts()}), so this drops to the underlying {@code OPCPackage} directly.
     */
    public static void docxWithEmbeddedObject(Path target, String bodyText, int count) throws IOException, InvalidFormatException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText(bodyText);

            PackagePart documentPart = doc.getPackagePart();
            OPCPackage pkg = documentPart.getPackage();
            for (int i = 1; i <= count; i++) {
                PackagePartName partName = PackagingURIHelper.createPartName("/word/embeddings/oleObject" + i + ".bin");
                PackagePart embeddedPart = pkg.createPart(partName, "application/vnd.openxmlformats-officedocument.oleObject");
                try (OutputStream os = embeddedPart.getOutputStream()) {
                    os.write("fixture OLE object bytes".getBytes(StandardCharsets.UTF_8));
                }
                documentPart.addRelationship(partName, TargetMode.INTERNAL,
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject", "rIdOle" + i);
            }
            save(doc, target);
        }
    }

    // ---------------------------------------------------------------- pdf

    public static void pdfPlain(Path target, String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(target.toFile());
        }
    }

    /** Visible line of text plus a second line rendered with PDF text render mode 3 (neither fill nor stroke — invisible). */
    public static void pdfWithInvisibleText(Path target, String visibleText, String hiddenText) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(visibleText);
                cs.endText();

                cs.beginText();
                cs.setFont(font, 12);
                cs.setRenderingMode(RenderingMode.NEITHER);
                cs.newLineAtOffset(72, 680);
                cs.showText(hiddenText);
                cs.endText();
            }
            doc.save(target.toFile());
        }
    }

    /** Visible line of text plus a second line colored white-on-white (assumed page background). */
    public static void pdfWithWhiteText(Path target, String visibleText, String hiddenText) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(visibleText);
                cs.endText();

                cs.beginText();
                cs.setFont(font, 12);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(72, 680);
                cs.showText(hiddenText);
                cs.endText();
            }
            doc.save(target.toFile());
        }
    }

    /** Visible line of text plus a second line placed at a large negative Y coordinate, off the page. */
    public static void pdfWithOffPageText(Path target, String visibleText, String offPageText) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(visibleText);
                cs.endText();

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, -500);
                cs.showText(offPageText);
                cs.endText();
            }
            doc.save(target.toFile());
        }
    }

    /** Visible normal-size line plus a second line rendered below a readable size. */
    public static void pdfWithTinyFont(Path target, String visibleText, String tinyText, float sizePt) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(visibleText);
                cs.endText();

                cs.beginText();
                cs.setFont(font, sizePt);
                cs.newLineAtOffset(72, 680);
                cs.showText(tinyText);
                cs.endText();
            }
            doc.save(target.toFile());
        }
    }

    public static void pdfWithMetadata(Path target, String field, String payload) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Ordinary visible document body.");
                cs.endText();
            }
            var info = doc.getDocumentInformation();
            switch (field.toLowerCase()) {
                case "title" -> info.setTitle(payload);
                case "author" -> info.setAuthor(payload);
                case "subject" -> info.setSubject(payload);
                case "keywords" -> info.setKeywords(payload);
                default -> throw new IllegalArgumentException("Unsupported metadata field: " + field);
            }
            doc.save(target.toFile());
        }
    }

    /** Visible body text plus {@code count} embedded file attachments in the document-level embedded-files name tree (the standard PDF "Attachments" panel). */
    public static void pdfWithEmbeddedFile(Path target, String bodyText, int count) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(bodyText);
                cs.endText();
            }

            Map<String, PDComplexFileSpecification> embeddedFiles = new HashMap<>();
            for (int i = 1; i <= count; i++) {
                byte[] data = ("fixture attachment bytes " + i).getBytes(StandardCharsets.UTF_8);
                PDEmbeddedFile embeddedFile = new PDEmbeddedFile(doc, new ByteArrayInputStream(data));
                embeddedFile.setSubtype("text/plain");
                embeddedFile.setSize(data.length);

                PDComplexFileSpecification fileSpec = new PDComplexFileSpecification();
                fileSpec.setFile("attachment" + i + ".txt");
                fileSpec.setEmbeddedFile(embeddedFile);
                embeddedFiles.put("attachment" + i + ".txt", fileSpec);
            }

            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            PDDocumentNameDictionary namesDictionary = new PDDocumentNameDictionary(catalog);
            PDEmbeddedFilesNameTreeNode embeddedFilesTree = new PDEmbeddedFilesNameTreeNode();
            embeddedFilesTree.setNames(embeddedFiles);
            namesDictionary.setEmbeddedFiles(embeddedFilesTree);
            catalog.setNames(namesDictionary);

            doc.save(target.toFile());
        }
    }

    // ---------------------------------------------------------------- json / yaml / xml

    /** A nested object value plus an array element value, both potential payload locations. */
    public static void jsonWithPayloads(Path target, String nestedValuePayload, String arrayValuePayload) throws IOException {
        String json = "{\n"
            + "  \"config\": {\n"
            + "    \"description\": \"" + nestedValuePayload + "\"\n"
            + "  },\n"
            + "  \"items\": [\"plain value\", \"" + arrayValuePayload + "\"]\n"
            + "}\n";
        Files.writeString(target, json, StandardCharsets.UTF_8);
    }

    /** A mapped scalar value plus a standalone {@code #} comment line, both potential payload locations. */
    public static void yamlWithPayloadAndComment(Path target, String valuePayload, String commentPayload) throws IOException {
        String yaml = "config:\n"
            + "  description: \"" + valuePayload + "\"\n"
            + "# " + commentPayload + "\n"
            + "items:\n"
            + "  - plain value\n";
        Files.writeString(target, yaml, StandardCharsets.UTF_8);
    }

    /** An element text payload, an attribute value payload, and a comment payload, all potential payload locations. */
    public static void xmlWithPayloads(Path target, String elementTextPayload, String attributePayload, String commentPayload) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<root>\n"
            + "  <item attr=\"" + attributePayload + "\">" + elementTextPayload + "</item>\n"
            + "  <!-- " + commentPayload + " -->\n"
            + "</root>\n";
        Files.writeString(target, xml, StandardCharsets.UTF_8);
    }

    /** An XML document with an internal DOCTYPE declaring an external entity pointing at {@code secretFile}, attempting classic XXE file disclosure. */
    public static void xmlWithXxeAttempt(Path target, Path secretFile) throws IOException {
        String xml = "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"file://" + secretFile.toAbsolutePath() + "\">]>\n"
            + "<root>&xxe;</root>\n";
        Files.writeString(target, xml, StandardCharsets.UTF_8);
    }

    private static void save(XWPFDocument doc, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            doc.write(out);
        }
    }
}
