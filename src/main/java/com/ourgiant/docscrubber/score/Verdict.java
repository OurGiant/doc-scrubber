package com.ourgiant.docscrubber.score;

public enum Verdict {
    CLEAN("Clean"),
    LOW_RISK("Low Risk"),
    SUSPICIOUS("Suspicious"),
    LIKELY_COMPROMISED("Likely Compromised");

    private final String display;

    Verdict(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
