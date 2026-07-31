package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.model.ExtractionModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Dispatches a file to whichever registered {@link DocumentParser} claims to support it. */
public final class ParserRegistry {

    private final List<DocumentParser> parsers;

    public ParserRegistry() {
        this(List.of(new PlainTextParser(), new DocxParser(), new PdfParser(), new JsonParser(), new YamlParser(), new XmlParser()));
    }

    public ParserRegistry(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public boolean isSupported(Path path) {
        return parsers.stream().anyMatch(p -> p.supports(path));
    }

    public ExtractionModel parse(Path path) throws IOException {
        for (DocumentParser parser : parsers) {
            if (parser.supports(path)) {
                return parser.parse(path);
            }
        }
        throw new IOException("No parser available for file: " + path);
    }
}
