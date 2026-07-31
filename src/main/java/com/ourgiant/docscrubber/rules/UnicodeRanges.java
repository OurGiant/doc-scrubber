package com.ourgiant.docscrubber.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses the {@code params.ranges} shape used by {@code unicodeClass} content rules: a list of {@code ["U+XXXX", "U+YYYY"]} pairs. Shared by {@link RulesValidator} (parse-check only) and the rules engine (actual matching). */
public final class UnicodeRanges {

    public record Range(int start, int end) {
        public boolean contains(int codePoint) {
            return codePoint >= start && codePoint <= end;
        }
    }

    private UnicodeRanges() {
    }

    /** @throws IllegalArgumentException if {@code params.ranges} is missing, empty, or malformed. */
    public static List<Range> parse(Map<String, Object> params) {
        Object raw = params.get("ranges");
        if (!(raw instanceof List<?> rawRanges) || rawRanges.isEmpty()) {
            throw new IllegalArgumentException("unicodeClass rule requires a non-empty params.ranges array");
        }
        List<Range> ranges = new ArrayList<>();
        for (Object entry : rawRanges) {
            if (!(entry instanceof List<?> pair) || pair.size() != 2) {
                throw new IllegalArgumentException("Each params.ranges entry must be a [start, end] pair, got: " + entry);
            }
            int start = parseCodePoint(String.valueOf(pair.get(0)));
            int end = parseCodePoint(String.valueOf(pair.get(1)));
            if (end < start) {
                throw new IllegalArgumentException("Range end must be >= start: " + entry);
            }
            ranges.add(new Range(start, end));
        }
        return ranges;
    }

    private static int parseCodePoint(String value) {
        String trimmed = value.trim();
        String hex = trimmed.toUpperCase().startsWith("U+") ? trimmed.substring(2) : trimmed;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid Unicode code point ('U+XXXX'): " + value, e);
        }
    }
}
