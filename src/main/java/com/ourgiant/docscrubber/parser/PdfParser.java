package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.RenderMode;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorN;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor;
import org.apache.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts text plus per-character visibility (render mode, fill color, font size, on/off-page
 * position) via a custom {@link PDFTextStripper}.
 *
 * <p><b>Honesty contract:</b> PDFBox gives us the color operators used to paint text, not what a
 * renderer would actually composite on screen. We deliberately do not try to infer a real
 * background color from nearby fill/rect operators or images — that reconstruction is unreliable
 * and, per product decision, a wrong guess (flagging normal text as hidden, or missing genuinely
 * hidden text against a color we guessed wrong) is worse than not guessing. Instead:
 * <ul>
 *   <li>Background is always treated as white ({@link Color#WHITE}), flagged
 *       {@link VisibilityAttributes#isBackgroundHeuristic()}. This catches the classic
 *       white-on-white / near-white injection trick with a low false-positive rate, at the known
 *       cost of missing text hidden against a non-white or image background.</li>
 *   <li>Render mode 3 ("invisible", PDF spec Tr operator) is treated as unambiguously hidden —
 *       this is not a guess, it is what the operator means.</li>
 *   <li>Overlap detection (text hidden beneath a later-painted image or shape) is not implemented
 *       here; see {@code OverlappedTextDetector} for why, and that limitation is surfaced on the
 *       {@link ExtractionModel}.</li>
 * </ul>
 * Every PDF scan carries these caveats in {@link ExtractionModel#getLimitations()} so a clean
 * score is never presented as stronger proof than it is.</p>
 */
public final class PdfParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(PdfParser.class);

    private static final List<String> LIMITATIONS = List.of(
        "PDF background color is assumed white for contrast analysis; text hidden against a "
            + "non-white or image background may not be detected as low-contrast.",
        "PDF text hidden beneath images or shapes (visual overlap) is not detected in this "
            + "release. A clean result does not confirm the document is free of hidden content."
    );

    @Override
    public boolean supports(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    @Override
    public ExtractionModel parse(Path path) throws IOException {
        List<TextFragment> fragments = new ArrayList<>();
        int embeddedObjectCount;
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            extractMetadata(document, fragments);

            VisibilityAwareStripper stripper = new VisibilityAwareStripper(fragments);
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            stripper.getText(document);

            Map<String, PDComplexFileSpecification> embeddedFiles = embeddedFiles(document);
            embeddedObjectCount = embeddedFiles.size();
            inspectEmbeddedFiles(embeddedFiles, fragments);
        }

        List<String> limitations = new ArrayList<>(LIMITATIONS);
        if (embeddedObjectCount > 0) {
            limitations.add("This document contains " + embeddedObjectCount + " embedded file attachment(s) "
                + "that were not scanned for hidden text — see the report's embeddedObjectCount field.");
        }
        return new ExtractionModel(path, DocumentFormat.PDF, fragments, limitations, embeddedObjectCount);
    }

    /** The document-level embedded-files name tree (a PDF viewer's "Attachments" panel) — not page-level FileAttachment annotations or a Kids-structured name tree, which are rarer in practice. */
    private Map<String, PDComplexFileSpecification> embeddedFiles(PDDocument document) throws IOException {
        PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
        if (names == null || names.getEmbeddedFiles() == null) {
            return Map.of();
        }
        Map<String, PDComplexFileSpecification> embeddedFiles = names.getEmbeddedFiles().getNames();
        return embeddedFiles == null ? Map.of() : embeddedFiles;
    }

    /** Bounded, best-effort structural inspection of each embedded file's raw bytes — see {@link EmbeddedStreamInspector}. A single unreadable embedded file is logged and skipped rather than failing the whole scan. */
    private void inspectEmbeddedFiles(Map<String, PDComplexFileSpecification> embeddedFiles, List<TextFragment> out) {
        for (Map.Entry<String, PDComplexFileSpecification> entry : embeddedFiles.entrySet()) {
            String name = entry.getValue().getFilename() != null ? entry.getValue().getFilename() : entry.getKey();
            PDEmbeddedFile embeddedFile = entry.getValue().getEmbeddedFile();
            if (embeddedFile == null) {
                continue;
            }
            try (InputStream in = embeddedFile.createInputStream()) {
                EmbeddedStreamInspector.Signals signals = EmbeddedStreamInspector.inspect(in);
                if (!signals.isEmpty()) {
                    out.add(new TextFragment(EmbeddedStreamInspector.describe(name, signals), Channel.EMBEDDED_OBJECT,
                        SourceLocation.field("Embedded object: " + name),
                        VisibilityAttributes.builder()
                            .embeddedExecutableSignature(signals.executableSignature())
                            .embeddedMacroStorageNames(signals.macroStorageNames())
                            .build()));
                }
            } catch (IOException e) {
                logger.warn("Could not read embedded file \"{}\" for structural inspection: {}", name, e.getMessage());
            }
        }
    }

    private void extractMetadata(PDDocument document, List<TextFragment> out) {
        PDDocumentInformation info = document.getDocumentInformation();
        if (info == null) {
            return;
        }
        addMetadata("Title", info.getTitle(), out);
        addMetadata("Author", info.getAuthor(), out);
        addMetadata("Subject", info.getSubject(), out);
        addMetadata("Keywords", info.getKeywords(), out);
    }

    private void addMetadata(String fieldName, String value, List<TextFragment> out) {
        if (value != null && !value.isBlank()) {
            out.add(new TextFragment(value, Channel.METADATA, SourceLocation.field(fieldName),
                VisibilityAttributes.builder().build()));
        }
    }

    /** Groups consecutive same-style, same-line characters into fragments so regex rules can match phrases, not single glyphs. */
    private static final class VisibilityAwareStripper extends PDFTextStripper {

        private final List<TextFragment> out;
        private final StringBuilder buffer = new StringBuilder();

        private String pendingSignature;
        private int pendingPage = -1;
        private VisibilityAttributes pendingVisibility;
        private float lastEndX;
        private float lastY;

        private float pageWidth;
        private float pageHeight;

        VisibilityAwareStripper(List<TextFragment> out) throws IOException {
            this.out = out;
            // PDFTextStripper registers only the operators text extraction strictly needs, which
            // excludes color — without these, non-stroking color operators (cs/sc/scn/g/rg/k) are
            // silently ignored and getGraphicsState().getNonStrokingColor() never leaves the PDF
            // default (black), making every white-on-white fixture look black-on-white.
            addOperator(new SetNonStrokingColorSpace(this));
            addOperator(new SetNonStrokingColor(this));
            addOperator(new SetNonStrokingColorN(this));
            addOperator(new SetNonStrokingDeviceGrayColor(this));
            addOperator(new SetNonStrokingDeviceRGBColor(this));
            addOperator(new SetNonStrokingDeviceCMYKColor(this));
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            flush();
            PDRectangle box = page.getCropBox();
            pageWidth = box.getWidth();
            pageHeight = box.getHeight();
            super.startPage(page);
        }

        @Override
        protected void endDocument(PDDocument document) throws IOException {
            flush();
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            String unicode = text.getUnicode();
            if (unicode == null || unicode.isEmpty()) {
                return;
            }

            RenderingMode mode = getGraphicsState().getTextState().getRenderingMode();
            RenderMode renderMode = RenderMode.fromPdfTr(renderingModeToTr(mode));
            boolean hidden = renderMode == RenderMode.INVISIBLE;
            Color color = resolveColor();
            float fontSize = text.getFontSizeInPt();
            float x = text.getXDirAdj();
            float y = text.getYDirAdj();
            boolean onPage = x >= -1f && x <= pageWidth + 1f && y >= -1f && y <= pageHeight + 1f;

            int page = getCurrentPageNo() - 1;
            String signature = page + "|" + renderMode + "|" + color.getRGB() + "|" + Math.round(fontSize * 2) + "|" + onPage;

            boolean sameLine = pendingSignature != null && Math.abs(y - lastY) <= Math.max(2f, fontSize * 0.5f);
            if (pendingSignature == null || !pendingSignature.equals(signature) || !sameLine) {
                flush();
                pendingSignature = signature;
                pendingPage = page;
                pendingVisibility = VisibilityAttributes.builder()
                    .fontColor(color)
                    .backgroundColor(Color.WHITE, true)
                    .fontSizePt((double) fontSize)
                    .renderMode(renderMode)
                    .hidden(hidden)
                    .onPage(onPage)
                    .position((double) x, (double) y)
                    .build();
            } else {
                float gap = x - lastEndX;
                if (gap > fontSize * 0.25f) {
                    buffer.append(' ');
                }
            }

            buffer.append(unicode);
            lastEndX = x + text.getWidthDirAdj();
            lastY = y;
        }

        private Color resolveColor() {
            try {
                PDColor nonStroking = getGraphicsState().getNonStrokingColor();
                float[] rgb = nonStroking.getColorSpace().toRGB(nonStroking.getComponents());
                return new Color(clamp(rgb[0]), clamp(rgb[1]), clamp(rgb[2]));
            } catch (Exception e) {
                return Color.BLACK;
            }
        }

        private float clamp(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        private int renderingModeToTr(RenderingMode mode) {
            return switch (mode) {
                case FILL -> 0;
                case STROKE -> 1;
                case FILL_STROKE -> 2;
                case NEITHER -> 3;
                case FILL_CLIP -> 4;
                case STROKE_CLIP -> 5;
                case FILL_STROKE_CLIP -> 6;
                case NEITHER_CLIP -> 7;
            };
        }

        private void flush() {
            if (!buffer.isEmpty() && pendingVisibility != null) {
                out.add(new TextFragment(buffer.toString(), Channel.BODY, SourceLocation.page(pendingPage), pendingVisibility));
            }
            buffer.setLength(0);
            pendingSignature = null;
            pendingVisibility = null;
        }
    }
}
