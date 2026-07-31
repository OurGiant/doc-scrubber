package com.ourgiant.docscrubber.model;

/** A single unit of extracted text plus everything the rules engine needs to judge it. */
public final class TextFragment {

    private final String text;
    private final Channel channel;
    private final SourceLocation location;
    private final VisibilityAttributes visibility;

    public TextFragment(String text, Channel channel, SourceLocation location, VisibilityAttributes visibility) {
        this.text = text;
        this.channel = channel;
        this.location = location;
        this.visibility = visibility;
    }

    public String getText() {
        return text;
    }

    public Channel getChannel() {
        return channel;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public VisibilityAttributes getVisibility() {
        return visibility;
    }
}
