package com.ourgiant.docscrubber.report;

import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.score.ScoreReport;
import com.ourgiant.docscrubber.score.Verdict;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** Writes a {@link ScoreReport} as a single self-contained HTML file for human review. */
public final class HtmlReportWriter {

    public void write(Path targetFile, Path sourceDocument, ScoreReport report) throws IOException {
        String html = render(sourceDocument, report);
        Files.writeString(targetFile, html, StandardCharsets.UTF_8);
    }

    private String render(Path sourceDocument, ScoreReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>DocScrubber Report</title>");
        sb.append("<style>").append(css()).append("</style></head><body>");

        sb.append("<h1>DocScrubber Scan Report</h1>");
        sb.append("<p class=\"meta\">").append(esc(sourceDocument.toString())).append(" &middot; ")
            .append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("</p>");

        sb.append("<div class=\"banner ").append(verdictClass(report.getVerdict())).append("\">")
            .append("<span class=\"verdict\">").append(esc(report.getVerdict().display())).append("</span>")
            .append("<span class=\"score\">Score: ").append(report.getScore()).append(" / 100</span>")
            .append("</div>");

        if (report.hasLimitations()) {
            sb.append("<div class=\"limitations\"><strong>Detection limitations for this document:</strong><ul>");
            for (String limitation : report.getLimitations()) {
                sb.append("<li>").append(esc(limitation)).append("</li>");
            }
            sb.append("</ul></div>");
        }

        sb.append("<h2>Findings (").append(report.getFindings().size()).append(")</h2>");
        if (report.getFindings().isEmpty()) {
            sb.append("<p>No findings.</p>");
        } else {
            sb.append("<table><thead><tr><th>Severity</th><th>Rule</th><th>Channel</th><th>Location</th><th>Evidence</th></tr></thead><tbody>");
            for (Finding f : report.getFindings()) {
                sb.append("<tr class=\"sev-").append(f.getSeverity().json()).append("\">");
                sb.append("<td>").append(esc(f.getSeverity().json())).append("</td>");
                sb.append("<td>").append(esc(f.getRuleName())).append(" <span class=\"rule-id\">(").append(esc(f.getRuleId())).append(")</span></td>");
                sb.append("<td>").append(esc(f.getChannel().name())).append("</td>");
                sb.append("<td>").append(esc(f.getLocation().describe())).append("</td>");
                sb.append("<td class=\"evidence\">").append(esc(f.getEvidence())).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</tbody></table>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String verdictClass(Verdict verdict) {
        return switch (verdict) {
            case CLEAN -> "clean";
            case LOW_RISK -> "low";
            case SUSPICIOUS -> "suspicious";
            case LIKELY_COMPROMISED -> "compromised";
        };
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String css() {
        return """
            body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; margin: 2rem; color: #1a1a1a; }
            .meta { color: #666; font-size: 0.9rem; }
            .banner { padding: 1rem 1.5rem; border-radius: 8px; display: flex; justify-content: space-between; align-items: center; margin: 1rem 0; font-weight: 600; font-size: 1.2rem; }
            .banner.clean { background: #e6f4ea; color: #1e7e34; }
            .banner.low { background: #fff8e1; color: #8a6d00; }
            .banner.suspicious { background: #ffe8cc; color: #a5490b; }
            .banner.compromised { background: #fde0e0; color: #a10e0e; }
            .limitations { background: #fff3cd; border: 1px solid #ffe08a; border-radius: 6px; padding: 0.75rem 1rem; margin: 1rem 0; }
            table { border-collapse: collapse; width: 100%; margin-top: 1rem; }
            th, td { border: 1px solid #ddd; padding: 0.5rem 0.7rem; text-align: left; font-size: 0.9rem; vertical-align: top; }
            th { background: #f5f5f5; }
            .rule-id { color: #888; font-size: 0.8rem; }
            .evidence { font-family: ui-monospace, Menlo, monospace; white-space: pre-wrap; }
            tr.sev-critical { background: #fde0e0; }
            tr.sev-high { background: #ffe8cc; }
            tr.sev-medium { background: #fff8e1; }
            """;
    }
}
