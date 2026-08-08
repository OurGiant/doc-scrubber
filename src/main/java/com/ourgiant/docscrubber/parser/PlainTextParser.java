package com.ourgiant.docscrubber.parser;

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
import java.util.Set;

/**
 * Parses .txt/.md and common source/data text formats (ts, tsx, js, jsx, py, html, htm, csv) as a
 * flat sequence of blank-line-delimited paragraphs. There is no hidden-text concept in plain text,
 * so every fragment gets the same neutral {@link VisibilityAttributes} (structural detectors will
 * simply never fire on these documents).
 */
public final class PlainTextParser implements DocumentParser {

    private static final VisibilityAttributes VISIBLE = VisibilityAttributes.builder().build();

    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
        "txt", "ts", "tsx", "js", "jsx", "py", "html", "htm", "csv");

    @Override
    public boolean supports(Path path) {
        String ext = extension(path);
        return PLAIN_TEXT_EXTENSIONS.contains(ext) || ext.equals("md") || ext.equals("markdown");
    }

    @Override
    public ExtractionModel parse(Path path) throws IOException {
        String ext = extension(path);
        DocumentFormat format = (ext.equals("md") || ext.equals("markdown")) ? DocumentFormat.MARKDOWN : DocumentFormat.PLAIN_TEXT;

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<TextFragment> fragments = new ArrayList<>();

        StringBuilder buffer = new StringBuilder();
        int paragraphStartLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.strip().isEmpty()) {
                flushParagraph(fragments, buffer, paragraphStartLine);
                paragraphStartLine = -1;
            } else {
                if (paragraphStartLine == -1) {
                    paragraphStartLine = i;
                } else {
                    buffer.append('\n');
                }
                buffer.append(line);
            }
        }
        flushParagraph(fragments, buffer, paragraphStartLine);

        return new ExtractionModel(path, format, fragments, List.of());
    }

    private void flushParagraph(List<TextFragment> fragments, StringBuilder buffer, int startLine) {
        if (buffer.isEmpty()) {
            return;
        }
        fragments.add(new TextFragment(buffer.toString(), Channel.BODY, SourceLocation.line(startLine), VISIBLE));
        buffer.setLength(0);
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
