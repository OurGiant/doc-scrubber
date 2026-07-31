package com.ourgiant.docscrubber.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ourgiant.docscrubber.engine.Finding;
import com.ourgiant.docscrubber.score.ScoreReport;
import com.ourgiant.docscrubber.util.JsonMapperFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/** Writes a {@link ScoreReport} as machine-readable JSON. */
public final class JsonReportWriter {

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();

    public void write(Path targetFile, Path sourceDocument, ScoreReport report) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("generatedAt", Instant.now().toString());
        root.put("sourceDocument", sourceDocument.toString());
        root.put("score", report.getScore());
        root.put("verdict", report.getVerdict().name());
        root.put("verdictDisplay", report.getVerdict().display());
        root.put("embeddedObjectCount", report.getEmbeddedObjectCount());

        ArrayNode limitations = root.putArray("limitations");
        report.getLimitations().forEach(limitations::add);

        ArrayNode findings = root.putArray("findings");
        for (Finding finding : report.getFindings()) {
            ObjectNode f = findings.addObject();
            f.put("ruleId", finding.getRuleId());
            f.put("ruleName", finding.getRuleName());
            f.put("severity", finding.getSeverity().json());
            f.put("weight", finding.getWeight());
            f.put("channel", finding.getChannel().name());
            f.put("location", finding.getLocation().describe());
            f.put("evidence", finding.getEvidence());
            f.put("remediation", finding.getRemediation());
            ArrayNode tags = f.putArray("tags");
            finding.getTags().forEach(tags::add);
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile.toFile(), root);
    }
}
