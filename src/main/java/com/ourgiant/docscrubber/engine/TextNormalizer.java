package com.ourgiant.docscrubber.engine;

import com.ourgiant.docscrubber.rules.UnicodeRanges;

import java.text.Normalizer;
import java.util.List;

/**
 * Produces a "shadow" copy of fragment text for content-rule (regex/keywordList) matching only.
 * NFKC-normalizes (collapsing fullwidth/compatibility characters, e.g. the fullwidth Latin block,
 * back to their plain ASCII equivalents) and strips the same zero-width/bidi/tags-block code points
 * CONTENT-020/021/022 detect. Without this, an evasion like fullwidth "ｉｇｎｏｒｅ" or
 * zero-width-interleaved "i&#8203;g&#8203;n&#8203;o&#8203;r&#8203;e" defeats every phrase-matching
 * content rule outright.
 *
 * <p>The {@code unicodeClass} rules themselves, and all evidence shown to the user, always use the
 * original unmodified fragment text — stripping first would make CONTENT-020/021/022 never fire,
 * and hiding the disguise from evidence would defeat the point of revealing it.
 */
final class TextNormalizer {

    private static final List<UnicodeRanges.Range> STRIPPED_RANGES = List.of(
        new UnicodeRanges.Range(0x200B, 0x200D), // zero-width space/joiner/non-joiner
        new UnicodeRanges.Range(0x2060, 0x2064), // word joiner and other zero-width format chars
        new UnicodeRanges.Range(0xFEFF, 0xFEFF), // BOM / zero-width no-break space
        new UnicodeRanges.Range(0x202A, 0x202E), // bidi override controls
        new UnicodeRanges.Range(0x2066, 0x2069), // bidi isolate controls
        new UnicodeRanges.Range(0xE0000, 0xE007F) // Unicode tags block
    );

    private TextNormalizer() {
    }

    static String shadow(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(cp -> {
            if (STRIPPED_RANGES.stream().noneMatch(r -> r.contains(cp))) {
                result.appendCodePoint(cp);
            }
        });
        return result.toString();
    }
}
