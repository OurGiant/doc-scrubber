package com.ourgiant.docscrubber.engine;

import com.ourgiant.docscrubber.rules.UnicodeRanges;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Produces a "shadow" copy of fragment text for content-rule (regex/keywordList) matching only.
 * NFKC-normalizes (collapsing fullwidth/compatibility characters, e.g. the fullwidth Latin block,
 * back to their plain ASCII equivalents), strips the same zero-width/bidi/tags-block code points
 * CONTENT-020/021/022 detect, and strips HTML/XML comments. Without this, an evasion like fullwidth
 * "ｉｇｎｏｒｅ", zero-width-interleaved "i&#8203;g&#8203;n&#8203;o&#8203;r&#8203;e", or
 * "ignore&lt;!-- x --&gt;previous instructions" defeats every phrase-matching content rule outright.
 *
 * <p>The {@code unicodeClass} rules themselves, and all evidence shown to the user, always use the
 * original unmodified fragment text — stripping first would make CONTENT-020/021/022 never fire,
 * and hiding the disguise from evidence would defeat the point of revealing it.
 *
 * <p>Deliberately does <em>not</em> strip HTML/XML tags generically (only comments): CONTENT-005
 * (fake system markers like {@code <system>}), CONTENT-006 ({@code <|im_start|>}-style tokens), and
 * CONTENT-011 ({@code <img src=...>} exfiltration) are themselves regex rules that run against this
 * shadow copy and rely on that literal {@code <...>} syntax as their detection signature — stripping
 * tags generically would remove the payload before those rules ever saw it.
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

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

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
        // Replaced with a space, not deleted outright: an attacker-placed comment often sits exactly
        // where a phrase-matching rule's whitespace gap would be (e.g. "ignore<!-- x -->previous
        // instructions"), and deleting it entirely would concatenate the words with no separator,
        // still defeating rules like CONTENT-001 that require [\s\p{Zs}]+ between tokens.
        return HTML_COMMENT.matcher(result).replaceAll(" ");
    }
}
