package com.ourgiant.docscrubber.engine;

/**
 * Wraps a fragment's text so a regex {@link java.util.regex.Matcher} matching against it can be
 * bounded in time. {@code java.util.regex} has no native timeout or cancellation — the standard
 * workaround is a {@link CharSequence} whose {@link #charAt} is on the matcher's hot path (called
 * on every backtrack step) and throws once a deadline passes, which is enough to bound the classic
 * catastrophic-backtracking blowup even though nothing was ever "interrupted" in the thread sense.
 *
 * <p>rules.json can be loaded from a file the user points the app at (see the Rules menu), and its
 * regex patterns run against untrusted document text — a single pathological pattern like
 * {@code (a+)+$} would otherwise hang a scan indefinitely.</p>
 */
final class TimeBoundedCharSequence implements CharSequence {

    private final CharSequence inner;
    private final long deadlineNanos;

    private TimeBoundedCharSequence(CharSequence inner, long deadlineNanos) {
        this.inner = inner;
        this.deadlineNanos = deadlineNanos;
    }

    static TimeBoundedCharSequence withTimeout(CharSequence inner, long timeoutMillis) {
        return new TimeBoundedCharSequence(inner, System.nanoTime() + timeoutMillis * 1_000_000L);
    }

    @Override
    public int length() {
        return inner.length();
    }

    @Override
    public char charAt(int index) {
        if (System.nanoTime() > deadlineNanos) {
            throw new RegexTimeoutException();
        }
        return inner.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        // Propagates the same absolute deadline rather than starting a fresh timeout window, so a
        // matcher can't reset its budget just by descending into a subsequence.
        return new TimeBoundedCharSequence(inner.subSequence(start, end), deadlineNanos);
    }

    @Override
    public String toString() {
        return inner.toString();
    }
}
