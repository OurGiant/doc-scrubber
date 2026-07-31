package com.ourgiant.docscrubber.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeBoundedCharSequenceTest {

    @Test
    void readsCharsNormallyWithinBudget() {
        CharSequence seq = TimeBoundedCharSequence.withTimeout("hello", 500);
        assertEquals(5, seq.length());
        assertEquals('h', seq.charAt(0));
        assertEquals("hello", seq.toString());
    }

    @Test
    void throwsOnceTheBudgetIsExhausted() {
        CharSequence seq = TimeBoundedCharSequence.withTimeout("hello", 0);
        assertThrows(RegexTimeoutException.class, () -> seq.charAt(0));
    }

    @Test
    void subSequencePropagatesTheSameDeadlineRatherThanResettingIt() {
        CharSequence seq = TimeBoundedCharSequence.withTimeout("hello world", 0);
        CharSequence sub = seq.subSequence(2, 8);
        assertThrows(RegexTimeoutException.class, () -> sub.charAt(0));
    }
}
