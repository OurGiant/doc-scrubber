package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

public final class TinyFontDetector implements Detector {

    @Override
    public String id() {
        return "tinyFont";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        Double sizePt = fragment.getVisibility().getFontSizePt();
        if (sizePt == null) {
            return false;
        }
        double maxPt = ParamUtil.getDouble(params, "maxPt", 2.0);
        return sizePt < maxPt;
    }
}
