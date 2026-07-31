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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Evaluates every enabled rule in a {@link RuleSet} against every fragment of an {@link ExtractionModel}, producing raw (pre-scoring) {@link Finding}s. */
public final class RulesEngine {

    private static final Logger logger = LoggerFactory.getLogger(RulesEngine.class);

    private final DetectorRegistry detectorRegistry;

    public RulesEngine(DetectorRegistry detectorRegistry) {
        this.detectorRegistry = detectorRegistry;
    }

    public List<Finding> evaluate(ExtractionModel model, RuleSet ruleSet) {
        List<Rule> enabledRules = ruleSet.getRules().stream().filter(Rule::isEnabled).toList();
        Map<String, Pattern> compiledPatterns = compileRegexRules(enabledRules);
        Map<String, List<UnicodeRanges.Range>> unicodeRanges = compileUnicodeRules(enabledRules);

        List<Finding> findings = new ArrayList<>();
        List<TextFragment> fragments = model.getFragments();
        for (int i = 0; i < fragments.size(); i++) {
            TextFragment fragment = fragments.get(i);
            for (Rule rule : enabledRules) {
                if (!appliesToChannel(rule, fragment.getChannel())) {
                    continue;
                }
                if (matches(rule, fragment, compiledPatterns, unicodeRanges)) {
                    findings.add(toFinding(rule, ruleSet, fragment, i));
                }
            }
        }
        return findings;
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

    private boolean matches(Rule rule, TextFragment fragment, Map<String, Pattern> compiledPatterns, Map<String, List<UnicodeRanges.Range>> unicodeRanges) {
        if (rule.getFamily() == RuleFamily.STRUCTURAL) {
            return detectorRegistry.lookup(rule.getDetector())
                .map(detector -> safeEvaluate(detector, fragment, rule))
                .orElse(false);
        }
        return switch (rule.getType()) {
            case REGEX -> {
                Pattern pattern = compiledPatterns.get(rule.getId());
                yield pattern != null && pattern.matcher(fragment.getText()).find();
            }
            case KEYWORD_LIST -> matchesAnyKeyword(rule, fragment.getText());
            case UNICODE_CLASS -> matchesAnyCodePoint(fragment.getText(), unicodeRanges.get(rule.getId()));
            case DETECTOR -> false; // unreachable: DETECTOR only occurs on STRUCTURAL rules, handled above
        };
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
