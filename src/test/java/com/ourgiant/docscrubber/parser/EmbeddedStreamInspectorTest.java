package com.ourgiant.docscrubber.parser;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedStreamInspectorTest {

    @Test
    void detectsWindowsDosExecutableSignature() throws IOException {
        byte[] header = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        EmbeddedStreamInspector.Signals signals = EmbeddedStreamInspector.inspect(new ByteArrayInputStream(header));

        assertEquals("MZ / Windows-DOS executable", signals.executableSignature());
        assertTrue(signals.macroStorageNames().isEmpty());
    }

    @Test
    void detectsElfExecutableSignature() throws IOException {
        byte[] header = {0x7f, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00};
        EmbeddedStreamInspector.Signals signals = EmbeddedStreamInspector.inspect(new ByteArrayInputStream(header));

        assertEquals("ELF / Linux executable", signals.executableSignature());
    }

    @Test
    void detectsMacroStorageEntryInOle2CompoundFile() throws IOException {
        byte[] bytes = ole2CompoundFileWithEntry("_VBA_PROJECT");
        EmbeddedStreamInspector.Signals signals = EmbeddedStreamInspector.inspect(new ByteArrayInputStream(bytes));

        assertNull(signals.executableSignature());
        assertTrue(signals.macroStorageNames().contains("_VBA_PROJECT"));
    }

    @Test
    void ordinaryTextStreamIsEmpty() throws IOException {
        byte[] bytes = "Nothing suspicious here.".getBytes(StandardCharsets.UTF_8);
        EmbeddedStreamInspector.Signals signals = EmbeddedStreamInspector.inspect(new ByteArrayInputStream(bytes));

        assertTrue(signals.isEmpty());
    }

    @Test
    void ordinaryZipLikeOoxmlStreamIsEmpty() throws IOException {
        // "PK\x03\x04" local-file-header signature — the start of any OOXML/zip container, not an OLE2
        // compound file. Must not be misidentified as containing a macro-storage stream.
        byte[] bytes = {'P', 'K', 0x03, 0x04, 0, 0, 0, 0};
        EmbeddedStreamInspector.Signals signals = EmbeddedStreamInspector.inspect(new ByteArrayInputStream(bytes));

        assertTrue(signals.isEmpty());
    }

    @Test
    void describeMentionsBothSignalsWhenBothPresent() {
        EmbeddedStreamInspector.Signals signals = new EmbeddedStreamInspector.Signals(
            "MZ / Windows-DOS executable", java.util.List.of("_VBA_PROJECT"));

        String description = EmbeddedStreamInspector.describe("invoice.exe", signals);

        assertTrue(description.contains("invoice.exe"));
        assertTrue(description.contains("executable file signature"));
        assertTrue(description.contains("_VBA_PROJECT"));
    }

    private static byte[] ole2CompoundFileWithEntry(String entryName) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            fs.getRoot().createDirectory(entryName);
            fs.writeFilesystem(bos);
        }
        return bos.toByteArray();
    }
}
