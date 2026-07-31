package com.ourgiant.docscrubber.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.rules.Severity;
import com.ourgiant.docscrubber.score.ScoreReport;
import com.ourgiant.docscrubber.score.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWritersTest {

    @Test
    void jsonReportRoundTripsScoreAndFindings(@TempDir Path dir) throws Exception {
        ScoreReport report = sampleReport();
        Path target = dir.resolve("report.json");

        new JsonReportWriter().write(target, Path.of("sample.docx"), report);

        JsonNode root = new ObjectMapper().readTree(Files.readString(target));
        assertEquals(55, root.get("score").asInt());
        assertEquals("SUSPICIOUS", root.get("verdict").asText());
        assertEquals(1, root.get("findings").size());
        assertEquals("<script>alert(1)</script>", root.get("findings").get(0).get("evidence").asText());
        assertEquals(1, root.get("limitations").size());
    }

    @Test
    void jsonReportIncludesEmbeddedObjectCount(@TempDir Path dir) throws Exception {
        ScoreReport report = new ScoreReport(55, Verdict.SUSPICIOUS, List.of(), List.of(), 3);
        Path target = dir.resolve("report-embedded.json");

        new JsonReportWriter().write(target, Path.of("sample.docx"), report);

        JsonNode root = new ObjectMapper().readTree(Files.readString(target));
        assertEquals(3, root.get("embeddedObjectCount").asInt());
    }

    @Test
    void jsonReportDefaultsEmbeddedObjectCountToZero(@TempDir Path dir) throws Exception {
        ScoreReport report = sampleReport();
        Path target = dir.resolve("report-no-embedded.json");

        new JsonReportWriter().write(target, Path.of("sample.docx"), report);

        JsonNode root = new ObjectMapper().readTree(Files.readString(target));
        assertEquals(0, root.get("embeddedObjectCount").asInt());
    }

    @Test
    void htmlReportEscapesEvidenceAndShowsLimitations(@TempDir Path dir) throws Exception {
        ScoreReport report = sampleReport();
        Path target = dir.resolve("report.html");

        new HtmlReportWriter().write(target, Path.of("sample.docx"), report);

        String html = Files.readString(target);
        assertTrue(html.contains("Suspicious"));
        assertTrue(html.contains("PDF background color is assumed white."));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "Evidence must be HTML-escaped, not injected raw");
        assertTrue(!html.contains("<script>alert(1)</script>"), "Raw unescaped evidence must never appear in the HTML report");
    }

    private ScoreReport sampleReport() {
        Finding finding = new Finding("CONTENT-001", "Instruction override phrase", Severity.CRITICAL, 40,
            Channel.BODY, SourceLocation.paragraphRun(2, 1), "<script>alert(1)</script>", List.of("injection"), "remove", 0);
        return new ScoreReport(55, Verdict.SUSPICIOUS, List.of(finding), List.of("PDF background color is assumed white."));
    }
}
