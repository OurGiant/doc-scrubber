package com.ourgiant.docscrubber.rules.detector;

import com.ourgiant.docscrubber.model.TextFragment;

import java.util.Map;

/** Fires when font color and background color are nearly identical — the white-on-white trick. On PDF the background is a documented assumption; see {@link com.ourgiant.docscrubber.parser.PdfParser}. */
public final class LowContrastTextDetector implements Detector {

    @Override
    public String id() {
        return "lowContrastText";
    }

    @Override
    public boolean evaluate(TextFragment fragment, Map<String, Object> params) {
        Double ratio = fragment.getVisibility().getContrastRatio();
        if (ratio == null) {
            return false;
        }
        double maxContrastRatio = ParamUtil.getDouble(params, "maxContrastRatio", 1.3);
        int minChars = ParamUtil.getInt(params, "minChars", 8);
        return ratio <= maxContrastRatio && fragment.getText().trim().length() >= minChars;
    }
}
