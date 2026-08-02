package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/** Fires when an embedded OLE compound-file object contains a macro-storage entry (_VBA_PROJECT, VBA, Macros) — see {@code EmbeddedStreamInspector}. */
public final class EmbeddedMacroStorageDetector implements Detector {

    @Override
    public String id() {
        return "embeddedMacroStorage";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        return fragment.getChannel() == Channel.EMBEDDED_OBJECT
            && !fragment.getVisibility().getEmbeddedMacroStorageNames().isEmpty();
    }
}
