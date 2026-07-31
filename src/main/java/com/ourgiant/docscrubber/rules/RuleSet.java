package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Deserialized root of {@code rules.json}. */
public final class RuleSet {

    private final int schemaVersion;
    private final Map<Severity, Integer> severityWeights;
    private final VerdictThresholds verdictThresholds;
    private final List<Combo> combos;
    private final List<Rule> rules;

    @JsonCreator
    public RuleSet(
        @JsonProperty("schemaVersion") Integer schemaVersion,
        @JsonProperty("severityWeights") Map<Severity, Integer> severityWeights,
        @JsonProperty("verdictThresholds") VerdictThresholds verdictThresholds,
        @JsonProperty("combos") List<Combo> combos,
        @JsonProperty("rules") List<Rule> rules
    ) {
        this.schemaVersion = schemaVersion == null ? 0 : schemaVersion;
        this.severityWeights = defaultedSeverityWeights(severityWeights);
        this.verdictThresholds = verdictThresholds == null ? new VerdictThresholds(null, null, null) : verdictThresholds;
        this.combos = combos == null ? List.of() : List.copyOf(combos);
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    private static Map<Severity, Integer> defaultedSeverityWeights(Map<Severity, Integer> configured) {
        Map<Severity, Integer> defaults = new EnumMap<>(Severity.class);
        defaults.put(Severity.INFO, 5);
        defaults.put(Severity.LOW, 10);
        defaults.put(Severity.MEDIUM, 20);
        defaults.put(Severity.HIGH, 30);
        defaults.put(Severity.CRITICAL, 40);
        if (configured != null) {
            defaults.putAll(configured);
        }
        return defaults;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Map<Severity, Integer> getSeverityWeights() {
        return severityWeights;
    }

    public VerdictThresholds getVerdictThresholds() {
        return verdictThresholds;
    }

    public List<Combo> getCombos() {
        return combos;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public int weightFor(Rule rule) {
        return rule.getWeight() != null ? rule.getWeight() : severityWeights.get(rule.getSeverity());
    }
}
