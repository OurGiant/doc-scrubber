package com.ourgiant.docscrubber.score;

import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.rules.Combo;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.VerdictThresholds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates findings into a capped 0-100 risk score and verdict. Deliberately format-agnostic:
 * it sums exactly what the rules engine found, with no per-format adjustment. Honest disclosure of
 * detection gaps (e.g. PDF background-color assumptions) lives entirely in
 * {@link ScoreReport#getLimitations()}, never as a hidden thumb on the score — inflating or
 * deflating the number itself would misrepresent what was actually checked.
 */
public final class Scorer {

    private static final int MAX_SCORE = 100;

    public ScoreReport score(List<Finding> findings, RuleSet ruleSet, List<String> limitations) {
        Map<Integer, Double> multiplierByFragment = comboMultipliers(findings, ruleSet.getCombos());

        double rawScore = 0;
        for (Finding finding : findings) {
            double multiplier = multiplierByFragment.getOrDefault(finding.getFragmentIndex(), 1.0);
            rawScore += finding.getWeight() * multiplier;
        }

        int cappedScore = (int) Math.round(Math.min(MAX_SCORE, rawScore));
        Verdict verdict = verdictFor(cappedScore, ruleSet.getVerdictThresholds());
        return new ScoreReport(cappedScore, verdict, findings, limitations);
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
