package com.ourgiant.docscrubber.engine;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.rules.Rule;
import com.ourgiant.docscrubber.rules.RuleFamily;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.RuleType;
import com.ourgiant.docscrubber.rules.UnicodeRanges;
import com.ourgiant.docscrubber.rules.detector.Detector;
import com.ourgiant.docscrubber.rules.detector.DetectorRegistry;
import com.ourgiant.docscrubber.util.EvidenceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Evaluates every enabled rule in a {@link RuleSet} against every fragment of an {@link ExtractionModel}, producing raw (pre-scoring) {@link Finding}s.
 *
 * <p>{@code regex}/{@code keywordList} rules are matched against a {@link TextNormalizer#shadow}
 * copy of each fragment's text (NFKC-normalized, zero-width/bidi/tags-block characters stripped),
 * not the raw text — otherwise a disguised phrase like fullwidth "ｉｇｎｏｒｅ" or
 * zero-width-interleaved letters defeats every phrase-matching rule outright. {@code unicodeClass}
 * rules and the evidence attached to every {@link Finding} always use the original, unmodified text:
 * the former exists specifically to detect these characters' presence, and the latter exists to show
 * a human reviewer the disguise itself.
 */
public final class RulesEngine {

    private static final Logger logger = LoggerFactory.getLogger(RulesEngine.class);

    /**
     * Per-match budget for a single regex rule against a single fragment. Generous relative to
     * normal matching (which finishes in microseconds even for long fragments) but bounded, so a
     * pathological pattern in a loaded rules.json can't hang a scan indefinitely — see
     * {@link TimeBoundedCharSequence}.
     */
    private static final long REGEX_TIMEOUT_MILLIS = 500;

    private final DetectorRegistry detectorRegistry;
    private final long regexTimeoutMillis;

    public RulesEngine(DetectorRegistry detectorRegistry) {
        this(detectorRegistry, REGEX_TIMEOUT_MILLIS);
    }

    /** Package-visible so tests can force the time budget down to something a deterministic, non-pathological match still exceeds. */
    RulesEngine(DetectorRegistry detectorRegistry, long regexTimeoutMillis) {
        this.detectorRegistry = detectorRegistry;
        this.regexTimeoutMillis = regexTimeoutMillis;
    }

    /** @param findings pre-scoring matches; @param warnings one entry per rule that hit its regex time budget and was skipped for the rest of the scan */
    public record EvaluationResult(List<Finding> findings, List<String> warnings) {
    }

    public EvaluationResult evaluate(ExtractionModel model, RuleSet ruleSet) {
        List<Rule> enabledRules = ruleSet.getRules().stream().filter(Rule::isEnabled).toList();
        Map<String, Pattern> compiledPatterns = compileRegexRules(enabledRules);
        Map<String, List<UnicodeRanges.Range>> unicodeRanges = compileUnicodeRules(enabledRules);

        List<Finding> findings = new ArrayList<>();
        Set<String> timedOutRuleIds = new HashSet<>();
        List<String> warnings = new ArrayList<>();
        List<TextFragment> fragments = model.getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            TextFragment fragment = fragments.get(i);
            String shadowText = TextNormalizer.shadow(fragment.getText());
            for (Rule rule : enabledRules) {
                if (!appliesToChannel(rule, fragment.getChannel())) {
                    continue;
                }
                if (matches(rule, fragment, shadowText, compiledPatterns, unicodeRanges, timedOutRuleIds, warnings)) {
                    findings.add(toFinding(rule, ruleSet, fragment, i));
                }
            }
        }
        return new EvaluationResult(findings, warnings);
    }

    private Map<String, Pattern> compileRegexRules(List<Rule> rules) {
        Map<String, Pattern> patterns = new HashMap<>();
        for (Rule rule : rules) {
            if (rule.getType() == RuleType.REGEX && rule.getPattern() != null) {
                try {
                    patterns.put(rule.getId(), Pattern.compile(rule.getPattern()));
                } catch (Exception e) {
                    logger.warn("Skipping rule {} with invalid regex (should have failed validation): {}", rule.getId(), e.getMessage());
                }
            }
        }
        return patterns;
    }

    private Map<String, List<UnicodeRanges.Range>> compileUnicodeRules(List<Rule> rules) {
        Map<String, List<UnicodeRanges.Range>> ranges = new HashMap<>();
        for (Rule rule : rules) {
            if (rule.getType() == RuleType.UNICODE_CLASS) {
                try {
                    ranges.put(rule.getId(), UnicodeRanges.parse(rule.getParams()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Skipping rule {} with invalid unicode ranges (should have failed validation): {}", rule.getId(), e.getMessage());
                }
            }
        }
        return ranges;
    }

    private boolean appliesToChannel(Rule rule, Channel channel) {
        return rule.appliesToAllChannels() || rule.getChannels().stream().anyMatch(c -> c.equalsIgnoreCase(channel.name()));
    }

    private boolean matches(Rule rule, TextFragment fragment, String shadowText, Map<String, Pattern> compiledPatterns,
        Map<String, List<UnicodeRanges.Range>> unicodeRanges, Set<String> timedOutRuleIds, List<String> warnings) {
        if (rule.getFamily() == RuleFamily.STRUCTURAL) {
            return detectorRegistry.lookup(rule.getDetector())
                .map(detector -> safeEvaluate(detector, fragment, rule))
                .orElse(false);
        }
        return switch (rule.getType()) {
            case REGEX -> matchesRegex(rule, shadowText, compiledPatterns, timedOutRuleIds, warnings);
            case KEYWORD_LIST -> matchesAnyKeyword(rule, shadowText);
            case UNICODE_CLASS -> matchesAnyCodePoint(fragment.getText(), unicodeRanges.get(rule.getId()));
            case DETECTOR -> false; // unreachable: DETECTOR only occurs on STRUCTURAL rules, handled above
        };
    }

    private boolean matchesRegex(Rule rule, String text, Map<String, Pattern> compiledPatterns,
        Set<String> timedOutRuleIds, List<String> warnings) {
        if (timedOutRuleIds.contains(rule.getId())) {
            return false; // already known pathological against this document; don't keep re-timing-out per fragment
        }
        Pattern pattern = compiledPatterns.get(rule.getId());
        if (pattern == null) {
            return false;
        }
        try {
            return pattern.matcher(TimeBoundedCharSequence.withTimeout(text, regexTimeoutMillis)).find();
        } catch (RegexTimeoutException e) {
            if (timedOutRuleIds.add(rule.getId())) {
                String warning = "Rule " + rule.getId() + " (" + rule.getName() + ") took too long to match and was "
                    + "skipped for the rest of this scan — its pattern may be too complex for the input. "
                    + "Findings for this rule are incomplete for this document.";
                warnings.add(warning);
                logger.warn("Regex rule {} exceeded the {}ms match budget; skipping it for the rest of this scan", rule.getId(), regexTimeoutMillis);
            }
            return false;
        }
    }

    private boolean safeEvaluate(Detector detector, TextFragment fragment, Rule rule) {
        try {
            return detector.evaluate(fragment, rule.getParams());
        } catch (Exception e) {
            logger.warn("Detector {} threw evaluating rule {}: {}", detector.id(), rule.getId(), e.getMessage());
            return false;
        }
    }

    private boolean matchesAnyKeyword(Rule rule, String text) {
        String haystack = rule.isCaseSensitive() ? text : text.toLowerCase(Locale.ROOT);
        for (String keyword : rule.getKeywords()) {
            String needle = rule.isCaseSensitive() ? keyword : keyword.toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyCodePoint(String text, List<UnicodeRanges.Range> ranges) {
        if (ranges == null) {
            return false;
        }
        return text.codePoints().anyMatch(cp -> ranges.stream().anyMatch(r -> r.contains(cp)));
    }

    private Finding toFinding(Rule rule, RuleSet ruleSet, TextFragment fragment, int fragmentIndex) {
        return new Finding(
            rule.getId(),
            rule.getName(),
            rule.getSeverity(),
            ruleSet.weightFor(rule),
            fragment.getChannel(),
            fragment.getLocation(),
            EvidenceUtil.prepare(fragment.getText()),
            rule.getTags(),
            rule.getRemediation(),
            fragmentIndex
        );
    }
}
