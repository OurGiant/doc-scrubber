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
    private final int embeddedObjectCount;

    public ScoreReport(int score, Verdict verdict, List<Finding> findings, List<String> limitations) {
        this(score, verdict, findings, limitations, 0);
    }

    public ScoreReport(int score, Verdict verdict, List<Finding> findings, List<String> limitations, int embeddedObjectCount) {
        this.score = score;
        this.verdict = verdict;
        this.findings = List.copyOf(findings);
        this.limitations = List.copyOf(limitations);
        this.embeddedObjectCount = embeddedObjectCount;
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

    /** Count of embedded files/OLE objects the parser detected but did not scan for text (0 if none). */
    public int getEmbeddedObjectCount() {
        return embeddedObjectCount;
    }
}
