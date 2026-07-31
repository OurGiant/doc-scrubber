package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Score cutoffs (inclusive lower bound) for each verdict tier. "Clean" is implicitly everything below {@link #getLowRisk()}. */
public final class VerdictThresholds {

    private final int lowRisk;
    private final int suspicious;
    private final int likelyCompromised;

    @JsonCreator
    public VerdictThresholds(
        @JsonProperty("lowRisk") Integer lowRisk,
        @JsonProperty("suspicious") Integer suspicious,
        @JsonProperty("likelyCompromised") Integer likelyCompromised
    ) {
        this.lowRisk = lowRisk == null ? 15 : lowRisk;
        this.suspicious = suspicious == null ? 40 : suspicious;
        this.likelyCompromised = likelyCompromised == null ? 70 : likelyCompromised;
    }

    public int getLowRisk() {
        return lowRisk;
    }

    public int getSuspicious() {
        return suspicious;
    }

    public int getLikelyCompromised() {
        return likelyCompromised;
    }
}
