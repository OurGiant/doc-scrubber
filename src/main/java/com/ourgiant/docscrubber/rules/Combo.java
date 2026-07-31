package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A score multiplier applied when findings carrying all of {@link #getRequireTags()} land on the
 * same fragment — e.g. a content "injection" hit that is also structurally hidden is far more
 * damning than either signal alone.
 */
public final class Combo {

    private final String id;
    private final String description;
    private final List<String> requireTags;
    private final boolean sameFragment;
    private final double multiplier;

    @JsonCreator
    public Combo(
        @JsonProperty("id") String id,
        @JsonProperty("description") String description,
        @JsonProperty("requireTags") List<String> requireTags,
        @JsonProperty("sameFragment") Boolean sameFragment,
        @JsonProperty("multiplier") Double multiplier
    ) {
        this.id = id;
        this.description = description;
        this.requireTags = requireTags == null ? List.of() : List.copyOf(requireTags);
        this.sameFragment = sameFragment == null || sameFragment;
        this.multiplier = multiplier == null ? 1.0 : multiplier;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getRequireTags() {
        return requireTags;
    }

    public boolean isSameFragment() {
        return sameFragment;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
