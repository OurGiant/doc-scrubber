package com.ourgiant.docscrubber.engine;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.rules.Severity;

import java.util.List;

/** One rule match against one fragment: the unit the scorer and report consume. */
public final class Finding {

    private final String ruleId;
    private final String ruleName;
    private final Severity severity;
    private final int weight;
    private final Channel channel;
    private final SourceLocation location;
    private final String evidence;
    private final List<String> tags;
    private final String remediation;
    private final int fragmentIndex;

    public Finding(String ruleId, String ruleName, Severity severity, int weight, Channel channel,
                    SourceLocation location, String evidence, List<String> tags, String remediation, int fragmentIndex) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.severity = severity;
        this.weight = weight;
        this.channel = channel;
        this.location = location;
        this.evidence = evidence;
        this.tags = List.copyOf(tags);
        this.remediation = remediation;
        this.fragmentIndex = fragmentIndex;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public Severity getSeverity() {
        return severity;
    }

    public int getWeight() {
        return weight;
    }

    public Channel getChannel() {
        return channel;
    }

    public SourceLocation getLocation() {
        return location;
    }

    /** Matched/flagged text, already truncated and safe to render as plain text (never HTML/markup). */
    public String getEvidence() {
        return evidence;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getRemediation() {
        return remediation;
    }

    public int getFragmentIndex() {
        return fragmentIndex;
    }
}
