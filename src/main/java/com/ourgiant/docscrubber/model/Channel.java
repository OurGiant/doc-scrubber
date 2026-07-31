package com.ourgiant.docscrubber.model;

/** The structural location a fragment of extracted text came from. */
public enum Channel {
    BODY,
    HEADER_FOOTER,
    COMMENT,
    TRACKED_CHANGE,
    FOOTNOTE,
    METADATA,
    ALT_TEXT,
    HYPERLINK_TARGET,
    EMBEDDED_OBJECT
}
