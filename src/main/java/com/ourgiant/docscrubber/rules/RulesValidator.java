package com.ourgiant.docscrubber.rules;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.rules.detector.DetectorRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates a {@link RuleSet} against the schema contract described in the project docs. Errors
 * mean the scan must not start (surfaced as a clear GUI error). Unknown detector ids are warnings
 * only, per spec, so a rules.json written against a newer build of DocScrubber degrades gracefully
 * on an older one.
 */
public final class RulesValidator {

    private final DetectorRegistry detectorRegistry;

    public RulesValidator(DetectorRegistry detectorRegistry) {
        this.detectorRegistry = detectorRegistry;
    }

    public ValidationResult validate(RuleSet ruleSet) {
        ValidationResult result = new ValidationResult();

        if (ruleSet.getSchemaVersion() != 1) {
            result.addError("Unsupported schemaVersion: " + ruleSet.getSchemaVersion() + " (expected 1)");
        }
        if (ruleSet.getRules().isEmpty()) {
            result.addWarning("rules.json defines no rules — every document will score as clean");
        }

        Set<String> seenIds = new HashSet<>();
        for (Rule rule : ruleSet.getRules()) {
            validateRule(rule, seenIds, result);
        }

        for (Combo combo : ruleSet.getCombos()) {
            validateCombo(combo, result);
        }

        return result;
    }

    private void validateRule(Rule rule, Set<String> seenIds, ValidationResult result) {
        String label = describeForError(rule);

        if (isBlank(rule.getId())) {
            result.addError("Rule is missing an id: " + label);
        } else if (!seenIds.add(rule.getId())) {
            result.addError("Duplicate rule id: " + rule.getId());
        }
        if (isBlank(rule.getName())) {
            result.addError(label + ": missing name");
        }
        if (rule.getFamily() == null) {
            result.addError(label + ": missing family");
            return;
        }
        if (rule.getType() == null) {
            result.addError(label + ": missing type");
            return;
        }
        if (rule.getSeverity() == null) {
            result.addError(label + ": missing severity");
        }
        if (rule.getWeight() != null && rule.getWeight() <= 0) {
            result.addError(label + ": weight must be positive if specified, got " + rule.getWeight());
        }

        validateFamilyTypeConsistency(rule, label, result);
        validateTypeSpecifics(rule, label, result);
        validateChannels(rule, label, result);
    }

    private void validateFamilyTypeConsistency(Rule rule, String label, ValidationResult result) {
        boolean contentType = rule.getType() == RuleType.REGEX || rule.getType() == RuleType.KEYWORD_LIST || rule.getType() == RuleType.UNICODE_CLASS;
        if (rule.getFamily() == RuleFamily.CONTENT && !contentType) {
            result.addError(label + ": family 'content' requires type regex, keywordList, or unicodeClass, got " + rule.getType());
        }
        if (rule.getFamily() == RuleFamily.STRUCTURAL && rule.getType() != RuleType.DETECTOR) {
            result.addError(label + ": family 'structural' requires type detector, got " + rule.getType());
        }
    }

    private void validateTypeSpecifics(Rule rule, String label, ValidationResult result) {
        switch (rule.getType()) {
            case REGEX -> {
                if (isBlank(rule.getPattern())) {
                    result.addError(label + ": regex rule requires a non-empty pattern");
                } else {
                    try {
                        Pattern.compile(rule.getPattern());
                    } catch (PatternSyntaxException e) {
                        result.addError(label + ": invalid regex pattern: " + e.getMessage());
                    }
                }
            }
            case KEYWORD_LIST -> {
                if (rule.getKeywords().isEmpty()) {
                    result.addError(label + ": keywordList rule requires a non-empty keywords array");
                }
            }
            case UNICODE_CLASS -> {
                try {
                    UnicodeRanges.parse(rule.getParams());
                } catch (IllegalArgumentException e) {
                    result.addError(label + ": " + e.getMessage());
                }
            }
            case DETECTOR -> {
                if (isBlank(rule.getDetector())) {
                    result.addError(label + ": detector rule requires a non-empty 'detector' id");
                } else if (!detectorRegistry.isKnown(rule.getDetector())) {
                    result.addWarning(label + ": unknown detector id '" + rule.getDetector() + "' — this rule will never match");
                }
            }
        }
    }

    private void validateChannels(Rule rule, String label, ValidationResult result) {
        for (String channel : rule.getChannels()) {
            if ("*".equals(channel)) {
                continue;
            }
            if (!isValidChannelName(channel)) {
                result.addError(label + ": unknown channel '" + channel + "'");
            }
        }
    }

    private boolean isValidChannelName(String channel) {
        for (Channel c : Channel.values()) {
            if (c.name().equalsIgnoreCase(channel)) {
                return true;
            }
        }
        return false;
    }

    private void validateCombo(Combo combo, ValidationResult result) {
        String label = "Combo " + (isBlank(combo.getId()) ? "<no id>" : combo.getId());
        if (combo.getRequireTags().isEmpty()) {
            result.addError(label + ": requireTags must not be empty");
        }
        if (combo.getMultiplier() <= 0) {
            result.addError(label + ": multiplier must be positive, got " + combo.getMultiplier());
        }
    }

    private String describeForError(Rule rule) {
        return "Rule " + (isBlank(rule.getId()) ? "<no id>" : rule.getId());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
