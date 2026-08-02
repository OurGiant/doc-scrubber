package com.ourgiant.docscrubber.parser;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded, best-effort structural checks against an embedded object's raw stream: executable
 * magic-byte signatures and OLE compound-file macro-storage entry names. Never parses beyond a
 * compound-file directory listing and never executes or fully decodes stream content — the same
 * "count, don't open" boundary #10 drew around embedded objects generally, just extended to also
 * notice a couple of specific, high-signal structural facts about them.
 */
public final class EmbeddedStreamInspector {

    /** Above this many bytes, skip the OLE2 directory scan — compound-file parsing needs the whole stream buffered, and a huge embedded object isn't worth that cost for a best-effort check. The executable-signature check only ever looks at the first few bytes, so it isn't affected by this cap. */
    private static final int MAX_OLE_INSPECTION_BYTES = 20 * 1024 * 1024;

    private static final Set<String> MACRO_STORAGE_ENTRY_NAMES = Set.of("_VBA_PROJECT", "VBA", "Macros");

    private EmbeddedStreamInspector() {
    }

    /** One embedded object's structural findings; both fields empty/null means nothing worth flagging. */
    public record Signals(String executableSignature, List<String> macroStorageNames) {

        public boolean isEmpty() {
            return executableSignature == null && macroStorageNames.isEmpty();
        }
    }

    /** Reads up to {@link #MAX_OLE_INSPECTION_BYTES}+1 bytes from {@code rawStream} (bounded regardless of what the format's own declared size claims) and checks them for known signatures. */
    public static Signals inspect(InputStream rawStream) throws IOException {
        byte[] bytes = rawStream.readNBytes(MAX_OLE_INSPECTION_BYTES + 1);
        String executableSignature = matchExecutableSignature(bytes);
        List<String> macroStorageNames = bytes.length <= MAX_OLE_INSPECTION_BYTES
            ? findMacroStorageNames(bytes)
            : List.of();
        return new Signals(executableSignature, macroStorageNames);
    }

    /** Human-readable evidence text for a {@code Signals} that {@link Signals#isEmpty()} is false, naming the embedded object by {@code objectName}. */
    public static String describe(String objectName, Signals signals) {
        List<String> clauses = new ArrayList<>();
        if (signals.executableSignature() != null) {
            clauses.add("begins with an executable file signature (" + signals.executableSignature() + ")");
        }
        if (!signals.macroStorageNames().isEmpty()) {
            clauses.add("contains an OLE macro-storage stream (\"" + String.join("\", \"", signals.macroStorageNames()) + "\")");
        }
        return "Embedded object \"" + objectName + "\" " + String.join("; ", clauses);
    }

    private static String matchExecutableSignature(byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == 'M' && bytes[1] == 'Z') {
            return "MZ / Windows-DOS executable";
        }
        if (bytes.length >= 4 && bytes[0] == 0x7f && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F') {
            return "ELF / Linux executable";
        }
        return null;
    }

    private static List<String> findMacroStorageNames(byte[] bytes) {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            Set<String> found = new LinkedHashSet<>();
            collectMacroStorageNames(fs.getRoot(), found);
            return List.copyOf(found);
        } catch (IOException | RuntimeException e) {
            // Not an OLE2 compound file (a plain OOXML/zip/PDF/etc. attachment) or otherwise unreadable
            // as one — nothing to inspect. Best-effort by design, so this is not a scan-ending error.
            return List.of();
        }
    }

    private static void collectMacroStorageNames(DirectoryEntry dir, Set<String> found) {
        for (Entry entry : dir) {
            if (MACRO_STORAGE_ENTRY_NAMES.contains(entry.getName())) {
                found.add(entry.getName());
            }
            if (entry instanceof DirectoryEntry nested) {
                collectMacroStorageNames(nested, found);
            }
        }
    }
}
