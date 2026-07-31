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
import java.nio.file.Path;
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
        ExtractionModel model = parsers.parse(file);
        List<Finding> findings = engine.evaluate(model, ruleSet);
        ScoreReport report = scorer.score(findings, ruleSet, model.getLimitations());
        return new ScanResult(file, model.getFormat(), report);
    }

    public record ScanResult(Path file, DocumentFormat format, ScoreReport report) {
    }
}
