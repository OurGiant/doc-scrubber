package com.ourgiant.docscrubber.engine;

/** Thrown by {@link TimeBoundedCharSequence} when a regex match runs past its time budget. */
final class RegexTimeoutException extends RuntimeException {

    RegexTimeoutException() {
        super("Regex match exceeded the time limit", null, false, false);
    }
}
