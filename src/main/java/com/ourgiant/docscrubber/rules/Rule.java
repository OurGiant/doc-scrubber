package com.ourgiant.docscrubber.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** One entry from {@code rules.json}. See {@link RulesValidator} for the constraints enforced on top of this shape. */
public final class Rule {

    private final String id;
    private final String name;
    private final RuleFamily family;
    private final RuleType type;
    private final String pattern;
    private final List<String> keywords;
    private final boolean caseSensitive;
    private final String detector;
    private final Map<String, Object> params;
    private final List<String> channels;
    private final Severity severity;
    private final Integer weight;
    private final String description;
    private final String remediation;
    private final boolean enabled;
    private final List<String> tags;

    @JsonCreator
    public Rule(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("family") RuleFamily family,
        @JsonProperty("type") RuleType type,
        @JsonProperty("pattern") String pattern,
        @JsonProperty("keywords") List<String> keywords,
        @JsonProperty("caseSensitive") Boolean caseSensitive,
        @JsonProperty("detector") String detector,
        @JsonProperty("params") Map<String, Object> params,
        @JsonProperty("channels") List<String> channels,
        @JsonProperty("severity") Severity severity,
        @JsonProperty("weight") Integer weight,
        @JsonProperty("description") String description,
        @JsonProperty("remediation") String remediation,
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("tags") List<String> tags
    ) {
        this.id = id;
        this.name = name;
        this.family = family;
        this.type = type;
        this.pattern = pattern;
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
        this.caseSensitive = caseSensitive != null && caseSensitive;
        this.detector = detector;
        this.params = params == null ? Map.of() : Map.copyOf(params);
        this.channels = channels == null ? List.of("*") : List.copyOf(channels);
        this.severity = severity;
        this.weight = weight;
        this.description = description;
        this.remediation = remediation;
        this.enabled = enabled == null || enabled;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RuleFamily getFamily() {
        return family;
    }

    public RuleType getType() {
        return type;
    }

    public String getPattern() {
        return pattern;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public String getDetector() {
        return detector;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public List<String> getChannels() {
        return channels;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Integer getWeight() {
        return weight;
    }

    public String getDescription() {
        return description;
    }

    public String getRemediation() {
        return remediation;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean appliesToAllChannels() {
        return channels.size() == 1 && "*".equals(channels.get(0));
    }
}
