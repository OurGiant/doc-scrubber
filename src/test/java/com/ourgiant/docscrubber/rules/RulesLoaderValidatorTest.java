package com.ourgiant.docscrubber.rules;

import com.ourgiant.docscrubber.rules.detector.DetectorRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesLoaderValidatorTest {

    private final RulesValidator validator = new RulesValidator(new DetectorRegistry());

    @Test
    void bundledDefaultRulesetLoadsAndValidatesCleanly() throws Exception {
        RuleSet ruleSet = new RulesLoader().loadDefault();

        assertEquals(1, ruleSet.getSchemaVersion());
        assertFalse(ruleSet.getRules().isEmpty());

        ValidationResult result = validator.validate(ruleSet);

        assertTrue(result.isValid(), "Bundled rules.json must have zero validation errors: " + result.getErrors());
        // STRUCT-007 (overlappedText) is intentionally disabled but still referenced, and every
        // other detector id is real, so the bundled ruleset should also produce zero warnings.
        assertTrue(result.getWarnings().isEmpty(), "Bundled rules.json produced unexpected warnings: " + result.getWarnings());
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        RuleSet ruleSet = new RuleSet(2, null, null, null, List.of());
        ValidationResult result = validator.validate(ruleSet);
        assertFalse(result.isValid());
    }

    @Test
    void rejectsDuplicateRuleIds() {
        Rule r1 = contentRegexRule("DUP-1", "abc");
        Rule r2 = contentRegexRule("DUP-1", "def");
        RuleSet ruleSet = new RuleSet(1, null, null, null, List.of(r1, r2));

        ValidationResult result = validator.validate(ruleSet);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate rule id")));
    }

    @Test
    void rejectsInvalidRegex() {
        Rule bad = contentRegexRule("BAD-REGEX", "(unclosed");
        RuleSet ruleSet = new RuleSet(1, null, null, null, List.of(bad));

        ValidationResult result = validator.validate(ruleSet);

        assertFalse(result.isValid());
    }

    @Test
    void rejectsFamilyTypeMismatch() {
        Rule mismatched = new Rule("MISMATCH", "bad", RuleFamily.CONTENT, RuleType.DETECTOR, null, null, null,
            "lowContrastText", Map.of(), List.of("*"), Severity.LOW, null, "desc", "remove", true, List.of());
        RuleSet ruleSet = new RuleSet(1, null, null, null, List.of(mismatched));

        ValidationResult result = validator.validate(ruleSet);

        assertFalse(result.isValid());
    }

    @Test
    void rejectsUnknownChannel() {
        Rule rule = new Rule("BAD-CHANNEL", "name", RuleFamily.CONTENT, RuleType.KEYWORD_LIST, null,
            List.of("test"), null, null, Map.of(), List.of("not_a_real_channel"), Severity.LOW, null, "desc", "remove", true, List.of());
        RuleSet ruleSet = new RuleSet(1, null, null, null, List.of(rule));

        ValidationResult result = validator.validate(ruleSet);

        assertFalse(result.isValid());
    }

    @Test
    void warnsButDoesNotFailOnUnknownDetectorId() {
        Rule rule = new Rule("UNKNOWN-DETECTOR", "name", RuleFamily.STRUCTURAL, RuleType.DETECTOR, null, null, null,
            "notARealDetector", Map.of(), List.of("*"), Severity.LOW, null, "desc", "remove", true, List.of());
        RuleSet ruleSet = new RuleSet(1, null, null, null, List.of(rule));

        ValidationResult result = validator.validate(ruleSet);

        assertTrue(result.isValid(), "Unknown detector id must be a warning, not a blocking error");
        assertFalse(result.getWarnings().isEmpty());
    }

    private Rule contentRegexRule(String id, String pattern) {
        return new Rule(id, "name", RuleFamily.CONTENT, RuleType.REGEX, pattern, null, null, null,
            Map.of(), List.of("*"), Severity.LOW, null, "desc", "remove", true, List.of());
    }
}
