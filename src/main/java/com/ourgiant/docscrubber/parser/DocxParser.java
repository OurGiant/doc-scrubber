package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import com.ourgiant.docscrubber.util.ColorUtil;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTAnchor;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline;
import org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperty;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Extracts every channel POI's XWPF model can reach: body, headers/footers, comments, footnotes,
 * tracked-change insertions/deletions, image/object alt-text, hyperlink targets, and core +
 * custom document properties.
 *
 * <p>Tracked changes are read directly off the paragraph XML via {@link XmlCursor} rather than
 * XWPFRun, since {@code w:ins}/{@code w:del}-wrapped runs are not exposed through
 * {@link XWPFParagraph#getRuns()}. Deletions in particular matter for this tool: text an author
 * "deleted" with tracked changes on is still physically present in the file (as {@code w:delText})
 * until the change is accepted, which makes it a plausible place to bury an instruction that was
 * never meant to render.</p>
 */
public final class DocxParser implements DocumentParser {

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    @Override
    public boolean supports(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    @Override
    public ExtractionModel parse(Path path) throws IOException {
        List<TextFragment> fragments = new ArrayList<>();
        int embeddedObjectCount;
        try (InputStream in = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(in)) {
            extractParagraphs(doc.getParagraphs(), Channel.BODY, fragments);
            extractTrackedChanges(doc.getParagraphs(), fragments);
            extractAltText(doc.getParagraphs(), fragments);
            extractHyperlinkTargets(doc, doc.getParagraphs(), fragments);
            extractHeadersFooters(doc, fragments);
            extractComments(doc, fragments);
            extractFootnotes(doc, fragments);
            extractProperties(doc, fragments);
            embeddedObjectCount = countEmbeddedParts(doc);
        }

        List<String> limitations = new ArrayList<>();
        if (embeddedObjectCount > 0) {
            limitations.add("This document contains " + embeddedObjectCount + " embedded object(s) (e.g. OLE "
                + "objects, embedded files) that were not scanned for hidden text — see the report's "
                + "embeddedObjectCount field.");
        }
        return new ExtractionModel(path, DocumentFormat.DOCX, fragments, limitations, embeddedObjectCount);
    }

    private int countEmbeddedParts(XWPFDocument doc) throws IOException {
        try {
            return doc.getAllEmbeddedParts().size();
        } catch (OpenXML4JException e) {
            throw new IOException("Failed to enumerate embedded objects", e);
        }
    }

    private void extractParagraphs(List<XWPFParagraph> paragraphs, Channel channel, List<TextFragment> out) {
        for (int p = 0; p < paragraphs.size(); p++) {
            XWPFParagraph paragraph = paragraphs.get(p);
            List<XWPFRun> runs = paragraph.getRuns();
            for (int r = 0; r < runs.size(); r++) {
                XWPFRun run = runs.get(r);
                String text = run.text();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                out.add(new TextFragment(text, channel, SourceLocation.paragraphRun(p, r), runVisibility(run, paragraph)));
            }
        }
    }

    private VisibilityAttributes runVisibility(XWPFRun run, XWPFParagraph paragraph) {
        CTR ctr = run.getCTR();
        CTRPr rPr = ctr.isSetRPr() ? ctr.getRPr() : null;

        boolean hidden = run.isVanish();

        Color fontColor = ColorUtil.parseHex(run.getColor(), Color.BLACK);

        CTShd shd = (rPr != null && rPr.sizeOfShdArray() > 0) ? rPr.getShdArray(0) : null;
        String fillHex = (shd != null && shd.isSetFill()) ? String.valueOf(shd.getFill()) : null;
        Color bgColor = ColorUtil.parseHex(fillHex, Color.WHITE);

        Double fontSize = run.getFontSizeAsDouble();

        return VisibilityAttributes.builder()
            .fontColor(fontColor)
            .backgroundColor(bgColor, false)
            .fontSizePt(fontSize)
            .hidden(hidden)
            .build();
    }

    private void extractTrackedChanges(List<XWPFParagraph> paragraphs, List<TextFragment> out) {
        for (int p = 0; p < paragraphs.size(); p++) {
            CTP ctp = paragraphs.get(p).getCTP();
            collectTrackedChangeText(ctp, "w:ins//w:t", p, out);
            collectTrackedChangeText(ctp, "w:del//w:delText", p, out);
        }
    }

    private void collectTrackedChangeText(CTP ctp, String path, int paragraphIndex, List<TextFragment> out) {
        String query = "declare namespace w='" + W_NS + "' .//" + path;
        for (XmlObject match : ctp.selectPath(query)) {
            try (XmlCursor cursor = match.newCursor()) {
                String text = cursor.getTextValue();
                if (text != null && !text.isEmpty()) {
                    out.add(new TextFragment(text, Channel.TRACKED_CHANGE, SourceLocation.paragraphRun(paragraphIndex, 0),
                        VisibilityAttributes.builder().hidden(true).build()));
                }
            }
        }
    }

    private void extractAltText(List<XWPFParagraph> paragraphs, List<TextFragment> out) {
        for (int p = 0; p < paragraphs.size(); p++) {
            for (XWPFRun run : paragraphs.get(p).getRuns()) {
                CTR ctr = run.getCTR();
                for (CTDrawing drawing : ctr.getDrawingArray()) {
                    for (CTInline inline : drawing.getInlineArray()) {
                        addAltText(inline.getDocPr(), p, out);
                    }
                    for (CTAnchor anchor : drawing.getAnchorArray()) {
                        addAltText(anchor.getDocPr(), p, out);
                    }
                }
            }
        }
    }

    private void addAltText(CTNonVisualDrawingProps docPr, int paragraphIndex, List<TextFragment> out) {
        if (docPr == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        if (docPr.isSetDescr() && docPr.getDescr() != null) {
            text.append(docPr.getDescr());
        }
        if (docPr.isSetTitle() && docPr.getTitle() != null && !docPr.getTitle().isBlank()) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(docPr.getTitle());
        }
        if (!text.isEmpty()) {
            out.add(new TextFragment(text.toString(), Channel.ALT_TEXT, SourceLocation.paragraphRun(paragraphIndex, 0),
                VisibilityAttributes.builder().build()));
        }
    }

    private void extractHyperlinkTargets(XWPFDocument doc, List<XWPFParagraph> paragraphs, List<TextFragment> out) {
        for (int p = 0; p < paragraphs.size(); p++) {
            for (XWPFRun run : paragraphs.get(p).getRuns()) {
                if (!(run instanceof XWPFHyperlinkRun hyperlinkRun)) {
                    continue;
                }
                XWPFHyperlink link = hyperlinkRun.getHyperlink(doc);
                if (link != null && link.getURL() != null && !link.getURL().isBlank()) {
                    out.add(new TextFragment(link.getURL(), Channel.HYPERLINK_TARGET, SourceLocation.paragraphRun(p, 0),
                        VisibilityAttributes.builder().build()));
                }
            }
        }
    }

    private void extractHeadersFooters(XWPFDocument doc, List<TextFragment> out) {
        XWPFHeaderFooterPolicy policy = doc.getHeaderFooterPolicy();
        if (policy == null) {
            return;
        }
        for (XWPFHeader header : Arrays.asList(policy.getDefaultHeader(), policy.getFirstPageHeader(), policy.getEvenPageHeader())) {
            if (header != null) {
                extractParagraphs(header.getParagraphs(), Channel.HEADER_FOOTER, out);
            }
        }
        for (XWPFFooter footer : Arrays.asList(policy.getDefaultFooter(), policy.getFirstPageFooter(), policy.getEvenPageFooter())) {
            if (footer != null) {
                extractParagraphs(footer.getParagraphs(), Channel.HEADER_FOOTER, out);
            }
        }
    }

    private void extractComments(XWPFDocument doc, List<TextFragment> out) {
        XWPFComment[] comments = doc.getComments();
        if (comments == null) {
            return;
        }
        for (XWPFComment comment : comments) {
            String text = comment.getText();
            if (text != null && !text.isBlank()) {
                out.add(new TextFragment(text, Channel.COMMENT, SourceLocation.field("Comment: " + comment.getAuthor()),
                    VisibilityAttributes.builder().build()));
            }
        }
    }

    private void extractFootnotes(XWPFDocument doc, List<TextFragment> out) {
        for (XWPFFootnote footnote : doc.getFootnotes()) {
            extractParagraphs(footnote.getParagraphs(), Channel.FOOTNOTE, out);
        }
    }

    private void extractProperties(XWPFDocument doc, List<TextFragment> out) {
        var core = doc.getProperties().getCoreProperties();
        addMetadata("Title", core.getTitle(), out);
        addMetadata("Subject", core.getSubject(), out);
        addMetadata("Creator", core.getCreator(), out);
        addMetadata("Description", core.getDescription(), out);
        addMetadata("Keywords", core.getKeywords(), out);

        var custom = doc.getProperties().getCustomProperties();
        if (custom != null) {
            for (CTProperty prop : custom.getUnderlyingProperties().getPropertyArray()) {
                String value = prop.isSetLpwstr() ? prop.getLpwstr() : (prop.isSetLpstr() ? prop.getLpstr() : null);
                addMetadata("Custom property: " + prop.getName(), value, out);
            }
        }
    }

    private void addMetadata(String fieldName, String value, List<TextFragment> out) {
        if (value != null && !value.isBlank()) {
            out.add(new TextFragment(value, Channel.METADATA, SourceLocation.field(fieldName),
                VisibilityAttributes.builder().build()));
        }
    }
}
