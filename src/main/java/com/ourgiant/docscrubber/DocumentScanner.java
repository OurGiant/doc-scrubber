package com.ourgiant.docscrubber;

import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.engine.RulesEngine;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.parser.ParserRegistry;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.score.ScoreReport;
import com.ourgiant.docscrubber.score.Scorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Ties parse -> rules -> score into the one call the GUI (and tests) actually need. */
public final class DocumentScanner {

    private final ParserRegistry parsers;
    private final RulesEngine engine;
    private final Scorer scorer;

    public DocumentScanner(ParserRegistry parsers, RulesEngine engine, Scorer scorer) {
        this.parsers = parsers;
        this.engine = engine;
        this.scorer = scorer;
    }

    public ScanResult scan(Path file, RuleSet ruleSet) throws IOException {
        long size = Files.size(file);
        if (size > ScanLimits.MAX_FILE_SIZE_BYTES) {
            throw new IOException("File is " + ScanLimits.formatSize(size) + ", over the "
                + ScanLimits.describeMaxFileSize() + " scan limit — skipped rather than risk an "
                + "incomplete or hung scan on a file this large.");
        }

        ExtractionModel model = parsers.parse(file);
        RulesEngine.EvaluationResult evaluation = engine.evaluate(model, ruleSet);

        List<String> limitations = new ArrayList<>(model.getLimitations());
        limitations.addAll(evaluation.warnings());

        ScoreReport report = scorer.score(evaluation.findings(), ruleSet, limitations);
        return new ScanResult(file, model.getFormat(), report);
    }

    public record ScanResult(Path file, DocumentFormat format, ScoreReport report) {
    }
}
