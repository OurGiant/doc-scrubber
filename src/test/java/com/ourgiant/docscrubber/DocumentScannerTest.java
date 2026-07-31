package com.ourgiant.docscrubber;

import com.ourgiant.docscrubber.engine.RulesEngine;
import com.ourgiant.docscrubber.parser.ParserRegistry;
import com.ourgiant.docscrubber.rules.RuleSet;
import com.ourgiant.docscrubber.rules.detector.DetectorRegistry;
import com.ourgiant.docscrubber.score.Scorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentScannerTest {

    private final DocumentScanner scanner = new DocumentScanner(
        new ParserRegistry(), new RulesEngine(new DetectorRegistry()), new Scorer());

    @Test
    void filesOverTheSizeLimitAreRejectedBeforeParsing(@TempDir Path tempDir) throws Exception {
        // A sparse file: instant to create and near-zero disk usage, but Files.size() (what the
        // scanner checks) reports its full logical length just like a real oversized file would.
        Path oversized = tempDir.resolve("oversized.txt");
        try (RandomAccessFile raf = new RandomAccessFile(oversized.toFile(), "rw")) {
            raf.setLength(ScanLimits.MAX_FILE_SIZE_BYTES + 1);
        }

        RuleSet ruleSet = new RuleSet(1, null, null, null, List.of());
        IOException e = assertThrows(IOException.class, () -> scanner.scan(oversized, ruleSet));
        assertTrue(e.getMessage().contains(ScanLimits.describeMaxFileSize()),
            "expected the error to mention the configured limit, got: " + e.getMessage());
    }
}
