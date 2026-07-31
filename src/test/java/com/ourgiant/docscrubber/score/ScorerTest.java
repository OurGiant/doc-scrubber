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
    void capsRepeatedHitsOfSameRuleInSameChannelInsteadOfScalingLinearly() {
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(), List.of());
        // Same rule, same channel, three different fragments -> would sum to 60 uncapped.
        List<Finding> findings = List.of(
            finding("R1", 20, 0, List.of()),
            finding("R1", 20, 1, List.of()),
            finding("R1", 20, 2, List.of())
        );

        // Capped at 1.5x the 20-point base weight = 30.
        assertEquals(30, scorer.score(findings, ruleSet, List.of()).getScore());
    }

    @Test
    void doesNotCapSameRuleAcrossDifferentChannels() {
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(), List.of());
        List<Finding> findings = List.of(
            finding("R1", 20, 0, List.of(), Channel.BODY),
            finding("R1", 20, 1, List.of(), Channel.METADATA)
        );

        // Each channel is its own group (20 each, both under their own 30-point cap) -> sums normally.
        assertEquals(40, scorer.score(findings, ruleSet, List.of()).getScore());
    }

    @Test
    void doesNotCapDifferentRulesInTheSameChannel() {
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(), List.of());
        List<Finding> findings = List.of(
            finding("R1", 20, 0, List.of()),
            finding("R1", 20, 1, List.of()),
            finding("R2", 20, 2, List.of())
        );

        // R1 group capped at 30; R2 is its own group, uncapped at 20.
        assertEquals(50, scorer.score(findings, ruleSet, List.of()).getScore());
    }

    @Test
    void repeatCapNeverSuppressesASingleFindingBelowItsOwnComboBoostedValue() {
        Combo combo = new Combo("C1", "desc", List.of("injection", "hidden-text"), true, 2.0);
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(combo), List.of());

        // A single R1 hit whose fragment also carries a hidden-text-tagged finding: combo multiplies
        // R1's 20-point weight to 40, which already exceeds the 1.5x (30-point) repeat cap on its own.
        List<Finding> findings = List.of(
            finding("R1", 20, 0, List.of("injection")),
            finding("R2", 10, 0, List.of("hidden-text"))
        );

        // R1 alone must score 40 (its own combo-boosted value), never suppressed down to the 30-point cap.
        // R2 (weight 10, also combo-boosted to 20) is its own group -> total 40 + 20 = 60.
        assertEquals(60, scorer.score(findings, ruleSet, List.of()).getScore());
    }

    @Test
    void repeatCapBoundsOnlyTheAdditionalRepeatsNotTheStrongestSingleInstance() {
        Combo combo = new Combo("C1", "desc", List.of("injection", "hidden-text"), true, 2.0);
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(combo), List.of());

        // R1 fires twice in the same channel: once combo-boosted to 40 (fragment 0, paired with a
        // hidden-text finding), once plain at 20 (fragment 1, no combo). Uncapped sum would be 60;
        // the 1.5x (30-point) cap is below the single strongest instance (40), so the group must
        // score exactly 40 -- the repeat at fragment 1 adds nothing once the floor is already above the cap.
        List<Finding> findings = List.of(
            finding("R1", 20, 0, List.of("injection")),
            finding("R2", 10, 0, List.of("hidden-text")),
            finding("R1", 20, 1, List.of())
        );

        // R1 group: 40 (floor, not suppressed to 30). R2 group: 20 (combo-boosted, its own group).
        assertEquals(60, scorer.score(findings, ruleSet, List.of()).getScore());
    }

    @Test
    void carriesLimitationsThroughUnchanged() {
        RuleSet ruleSet = new RuleSet(1, null, null, List.of(), List.of());
        List<String> limitations = List.of("PDF background color is assumed white.");

        ScoreReport report = scorer.score(List.of(), ruleSet, limitations);

        assertEquals(limitations, report.getLimitations());
    }

    private Finding finding(String ruleId, int weight, int fragmentIndex, List<String> tags) {
        return finding(ruleId, weight, fragmentIndex, tags, Channel.BODY);
    }

    private Finding finding(String ruleId, int weight, int fragmentIndex, List<String> tags, Channel channel) {
        return new Finding(ruleId, ruleId, Severity.MEDIUM, weight, channel, SourceLocation.page(0), "evidence", tags, "remove", fragmentIndex);
    }
}
