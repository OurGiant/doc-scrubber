package com.ourgiant.docscrubber.score;

import com.ourgiant.docscrubber.engine.Finding;

import java.util.List;

/**
 * Final scan result for one document. {@code limitations} is carried through unchanged from the
 * {@code ExtractionModel} that produced the findings — a PDF's heuristic-detection caveats travel
 * with the score itself, so nothing downstream can present a clean PDF result without also
 * showing why "clean" here means something narrower than for a docx.
 */
public final class ScoreReport {

    private final int score;
    private final Verdict verdict;
    private final List<Finding> findings;
    private final List<String> limitations;

    public ScoreReport(int score, Verdict verdict, List<Finding> findings, List<String> limitations) {
        this.score = score;
        this.verdict = verdict;
        this.findings = List.copyOf(findings);
        this.limitations = List.copyOf(limitations);
    }

    public int getScore() {
        return score;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public List<String> getLimitations() {
        return limitations;
    }

    public boolean hasLimitations() {
        return !limitations.isEmpty();
    }
}
