package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/** Fires when an embedded object's raw stream begins with a known executable magic-byte signature (MZ/ELF) — see {@code EmbeddedStreamInspector}. */
public final class EmbeddedExecutableSignatureDetector implements Detector {

    @Override
    public String id() {
        return "embeddedExecutableSignature";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        return fragment.getChannel() == Channel.EMBEDDED_OBJECT
            && fragment.getVisibility().getEmbeddedExecutableSignature() != null;
    }
}
