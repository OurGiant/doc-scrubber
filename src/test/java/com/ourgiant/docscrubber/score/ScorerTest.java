package com.ourgiant.docscrubber.score;

import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.rules.Combo;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.Severity;
import com.ourgiant.docscrubber.rules.VerdictThresholds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScorerTest {

    private final Scorer scorer = new Scorer();

    @Test
    void sumsFindingWeightsWithoutCombos() {
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(), List.of());
        List<Finding> findings = List.of(finding("R1", 10, 0, List.of("injection")), finding("R2", 20, 1, List.of("hidden-text")));

        ScoreReport report = scorer.score(findings, ruleSet, List.of());

        assertEquals(30, report.getScore());
    }

    @Test
    void appliesComboMultiplierOnlyWhenTagsCoOccurOnSameFragment() {
        Combo combo = new Combo("C1", "desc", List.of("injection", "hidden-text"), true, 2.0);
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(combo), List.of());

        // Same fragment (index 0) carries both required tags -> multiplier applies to both findings on it.
        List<Finding> comboFindings = List.of(
            finding("R1", 10, 0, List.of("injection")),
            finding("R2", 10, 0, List.of("hidden-text"))
        );
        assertEquals(40, scorer.score(comboFindings, ruleSet, List.of()).getScore());

        // Same tags, but on different fragments -> no combo.
        List<Finding> noComboFindings = List.of(
            finding("R1", 10, 0, List.of("injection")),
            finding("R2", 10, 1, List.of("hidden-text"))
        );
        assertEquals(20, scorer.score(noComboFindings, ruleSet, List.of()).getScore());
    }

    @Test
    void capsScoreAt100() {
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(), List.of());
        List<Finding> findings = List.of(finding("R1", 40, 0, List.of()), finding("R2", 40, 1, List.of()), finding("R3", 40, 2, List.of()));

        assertEquals(100, scorer.score(findings, ruleSet, List.of()).getScore());
    }

    @Test
    void mapsScoreToVerdictUsingThresholds() {
        VerdictThresholds thresholds = new VerdictThresholds(15, 40, 70);
        RuleSet ruleSet = new RuleSet(1, null, thresholds, List.of(), List.of());

        assertEquals(Verdict.CLEAN, scorer.score(List.of(finding("R1", 10, 0, List.of())), ruleSet, List.of()).getVerdict());
        assertEquals(Verdict.LOW_RISK, scorer.score(List.of(finding("R1", 20, 0, List.of())), ruleSet, List.of()).getVerdict());
        assertEquals(Verdict.SUSPICIOUS, scorer.score(List.of(finding("R1", 40, 0, List.of())), ruleSet, List.of()).getVerdict());
        assertEquals(Verdict.LIKELY_COMPROMISED, scorer.score(List.of(finding("R1", 70, 0, List.of())), ruleSet, List.of()).getVerdict());
    }

    @Test
    void carriesLimitationsThroughUnchanged() {
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(), List.of());
        List<String> limitations = List.of("PDF background color is assumed white.");

        ScoreReport report = scorer.score(List.of(), ruleSet, limitations);

        assertEquals(limitations, report.getLimitations());
    }

    private Finding finding(String ruleId, int weight, int fragmentIndex, List<String> tags) {
        return new Finding(ruleId, ruleId, Severity.MEDIUM, weight, Channel.BODY, SourceLocation.page(0), "evidence", tags, "remove", fragmentIndex);
    }
}
