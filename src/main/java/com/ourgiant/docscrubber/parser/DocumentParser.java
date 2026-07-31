package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.model.ExtractionModel;

import java.io.IOException;
import java.nio.file.Path;

/** Produces a normalized {@link ExtractionModel} from one document format. Never executes or follows anything in the document. */
public interface DocumentParser {

    /** True if this parser handles the given file, based on its extension. */
    boolean supports(Path path);

    ExtractionModel parse(Path path) throws IOException;
}
