package com.ourgiant.docscrubber.score;

import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.rules.Combo;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.VerdictThresholds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates findings into a capped 0-100 risk score and verdict. Format-agnostic: it sums what the
 * rules engine found, with no per-format adjustment. Honest disclosure of detection gaps (e.g. PDF
 * background-color assumptions) lives entirely in {@link ScoreReport#getLimitations()}, never as a
 * hidden thumb on the score — inflating or deflating the number itself would misrepresent what was
 * actually checked.
 *
 * <p>One deliberate exception to pure summation: repeated hits of the <em>same rule in the same
 * channel</em> are capped (see {@link #REPEAT_HIT_CAP_MULTIPLIER}), so a long legitimate document
 * that happens to trip one medium-severity rule several times in unrelated, non-suspicious spots
 * doesn't cross a verdict threshold on volume alone, while a single instance of that same rule
 * (or a combo-boosted one) is never suppressed below its own value — the cap only bounds what
 * <em>additional</em> repeats contribute, it never reduces a single finding's own signal.
 */
public final class Scorer {

    private static final int MAX_SCORE = 100;

    /** A repeated rule+channel group's total contribution is capped at this multiple of the rule's base weight. */
    private static final double REPEAT_HIT_CAP_MULTIPLIER = 1.5;

    private record RuleChannelKey(String ruleId, Channel channel) {
    }

    public ScoreReport score(List<Finding> findings, RuleSet ruleSet, List<String> limitations) {
        return score(findings, ruleSet, limitations, 0);
    }

    public ScoreReport score(List<Finding> findings, RuleSet ruleSet, List<String> limitations, int embeddedObjectCount) {
        Map<Integer, Double> multiplierByFragment = comboMultipliers(findings, ruleSet.getCombos());

        Map<RuleChannelKey, List<Double>> contributionsByRuleChannel = new HashMap<>();
        Map<RuleChannelKey, Integer> baseWeightByRuleChannel = new HashMap<>();
        for (Finding finding : findings) {
            double multiplier = multiplierByFragment.getOrDefault(finding.getFragmentIndex(), 1.0);
            RuleChannelKey key = new RuleChannelKey(finding.getRuleId(), finding.getChannel());
            contributionsByRuleChannel.computeIfAbsent(key, k -> new ArrayList<>()).add(finding.getWeight() * multiplier);
            baseWeightByRuleChannel.putIfAbsent(key, finding.getWeight());
        }

        double rawScore = 0;
        for (Map.Entry<RuleChannelKey, List<Double>> entry : contributionsByRuleChannel.entrySet()) {
            List<Double> contributions = entry.getValue();
            double sum = contributions.stream().mapToDouble(Double::doubleValue).sum();
            double max = contributions.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double cap = REPEAT_HIT_CAP_MULTIPLIER * baseWeightByRuleChannel.get(entry.getKey());
            rawScore += Math.max(max, Math.min(sum, cap));
        }

        int cappedScore = (int) Math.round(Math.min(MAX_SCORE, rawScore));
        Verdict verdict = verdictFor(cappedScore, ruleSet.getVerdictThresholds());
        return new ScoreReport(cappedScore, verdict, findings, limitations, embeddedObjectCount);
    }

    /** For each fragment index, the product of every combo's multiplier whose required tags are all present among that fragment's findings. */
    private Map<Integer, Double> comboMultipliers(List<Finding> findings, List<Combo> combos) {
        Map<Integer, List<Finding>> byFragment = new HashMap<>();
        for (Finding finding : findings) {
            byFragment.computeIfAbsent(finding.getFragmentIndex(), k -> new java.util.ArrayList<>()).add(finding);
        }

        Map<Integer, Double> multipliers = new HashMap<>();
        for (Map.Entry<Integer, List<Finding>> entry : byFragment.entrySet()) {
            java.util.Set<String> tagsPresent = new java.util.HashSet<>();
            entry.getValue().forEach(f -> tagsPresent.addAll(f.getTags()));

            double multiplier = 1.0;
            for (Combo combo : combos) {
                if (combo.isSameFragment() && tagsPresent.containsAll(combo.getRequireTags())) {
                    multiplier *= combo.getMultiplier();
                }
            }
            multipliers.put(entry.getKey(), multiplier);
        }
        return multipliers;
    }

    private Verdict verdictFor(int score, VerdictThresholds thresholds) {
        if (score >= thresholds.getLikelyCompromised()) {
            return Verdict.LIKELY_COMPROMISED;
        }
        if (score >= thresholds.getSuspicious()) {
            return Verdict.SUSPICIOUS;
        }
        if (score >= thresholds.getLowRisk()) {
            return Verdict.LOW_RISK;
        }
        return Verdict.CLEAN;
    }
}
