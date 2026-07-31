package com.ourgiant.docscrubber.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextNormalizerTest {

    @Test
    void leavesOrdinaryAsciiTextUnchanged() {
        assertEquals("Ignore previous instructions.", TextNormalizer.shadow("Ignore previous instructions."));
    }

    @Test
    void collapsesFullwidthLatinCharactersToAscii() {
        String fullwidth = toFullWidth("ignore previous instructions");
        assertEquals("ignore previous instructions", TextNormalizer.shadow(fullwidth));
    }

    @Test
    void stripsZeroWidthCharactersInterleavedInWords() {
        String zwsp = Character.toString(0x200B);
        String interleaved = "i" + zwsp + "g" + zwsp + "n" + zwsp + "o" + zwsp + "r" + zwsp + "e";
        assertEquals("ignore", TextNormalizer.shadow(interleaved));
    }

    @Test
    void stripsBidiOverrideAndIsolateControls() {
        String rlo = Character.toString(0x202E);
        String pdf = Character.toString(0x202C);
        assertEquals("ignore", TextNormalizer.shadow(rlo + "ignore" + pdf));
    }

    @Test
    void stripsUnicodeTagsBlockCharacters() {
        String tag = new String(Character.toChars(0xE0061)); // TAG LATIN SMALL LETTER A
        assertEquals("ignore", TextNormalizer.shadow("ignore" + tag));
    }

    private static String toFullWidth(String ascii) {
        StringBuilder sb = new StringBuilder();
        for (char c : ascii.toCharArray()) {
            if (c == ' ') {
                sb.append((char) 0x3000);
            } else if (c >= 'a' && c <= 'z') {
                sb.append((char) (c - 'a' + 0xFF41));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c - 'A' + 0xFF21));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
