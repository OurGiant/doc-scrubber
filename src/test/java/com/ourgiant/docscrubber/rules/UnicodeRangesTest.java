package com.ourgiant.docscrubber.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnicodeRangesTest {

    @Test
    void parsesValidRangePairs() {
        Map<String, Object> params = Map.of("ranges", List.of(List.of("U+2060", "U+2064")));

        List<UnicodeRanges.Range> ranges = UnicodeRanges.parse(params);

        assertEquals(1, ranges.size());
        assertTrue(ranges.get(0).contains(0x2062));
        assertTrue(ranges.get(0).contains(0x2060));
        assertTrue(ranges.get(0).contains(0x2064));
        assertEquals(false, ranges.get(0).contains(0x2065));
    }

    @Test
    void rejectsMissingRanges() {
        assertThrows(IllegalArgumentException.class, () -> UnicodeRanges.parse(Map.of()));
    }

    @Test
    void rejectsMalformedCodePoint() {
        Map<String, Object> params = Map.of("ranges", List.of(List.of("not-a-codepoint", "U+2064")));
        assertThrows(IllegalArgumentException.class, () -> UnicodeRanges.parse(params));
    }

    @Test
    void rejectsEndBeforeStart() {
        Map<String, Object> params = Map.of("ranges", List.of(List.of("U+2064", "U+2060")));
        assertThrows(IllegalArgumentException.class, () -> UnicodeRanges.parse(params));
    }
}
