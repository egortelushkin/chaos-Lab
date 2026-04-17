package com.chaosLab.dsl;

import com.chaosLab.ChaosExperiment;
import com.chaosLab.ChaosEngine;
import com.chaosLab.ExperimentReport;
import com.chaosLab.InvariantResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class RunArtifactsWriter {

    private RunArtifactsWriter() {
    }

    static String readDslYaml(Path dslPath) {
        Path resolvedPath = resolveDslPath(dslPath);
        try {
            return Files.readString(resolvedPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read DSL file: " + resolvedPath, e);
        }
    }

    static void writeSnapshot(Path sourceDslPath, Path snapshotPath) {
        Path resolvedSource = resolveDslPath(sourceDslPath);
        Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        try {
            Path parent = snapshotPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(resolvedSource, snapshotPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write DSL snapshot to " + snapshotPath, e);
        }
    }

    static Path resolveDslPath(Path dslPath) {
        Objects.requireNonNull(dslPath, "dslPath must not be null");

        if (dslPath.isAbsolute()) {
            if (Files.exists(dslPath)) {
                return dslPath.normalize();
            }
            throw new IllegalStateException("DSL file not found: " + dslPath);
        }

        if (Files.exists(dslPath)) {
            return dslPath.toAbsolutePath().normalize();
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path cursor = cwd;
        for (int i = 0; i < 6 && cursor != null; i++) {
            Path candidate = cursor.resolve(dslPath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }

        throw new IllegalStateException("DSL file not found: " + dslPath + " (cwd=" + cwd + ")");
    }

    static void writeMetadata(
            Path metadataPath,
            Path dslPath,
            Path reportPath,
            String dslSha256,
            boolean gateEnforced,
            ChaosExperiment experiment,
            ExperimentReport report,
            List<PrometheusObservability.PrometheusCheckResult> prometheusChecks
    ) {
        Objects.requireNonNull(metadataPath, "metadataPath must not be null");
        Objects.requireNonNull(dslPath, "dslPath must not be null");
        Objects.requireNonNull(reportPath, "reportPath must not be null");
        Objects.requireNonNull(dslSha256, "dslSha256 must not be null");
        Objects.requireNonNull(experiment, "experiment must not be null");
        Objects.requireNonNull(report, "report must not be null");
        Objects.requireNonNull(prometheusChecks, "prometheusChecks must not be null");

        ChaosEngine faultEngine = experiment.getFaultEngine();
        Long faultSeed = faultEngine == null ? null : faultEngine.getRandomSeed();
        List<String> failedInvariants = report.getInvariantResults().stream()
                .filter(result -> !result.isPassed())
                .map(InvariantResult::getName)
                .collect(Collectors.toList());

        StringBuilder json = new StringBuilder();
        json.append("{");
        appendStringField(json, "generatedAt", Instant.now().toString()).append(",");
        appendStringField(json, "dslPath", dslPath.toAbsolutePath().normalize().toString()).append(",");
        appendStringField(json, "reportPath", reportPath.toAbsolutePath().normalize().toString()).append(",");
        appendStringField(json, "dslSha256", dslSha256).append(",");
        appendStringField(json, "experimentName", report.getExperimentName()).append(",");
        appendNullableNumberField(json, "experimentSeed", experiment.getSeed()).append(",");
        appendNullableNumberField(json, "faultSeed", faultSeed).append(",");
        appendBooleanField(json, "gateEnforced", gateEnforced).append(",");
        appendStringField(json, "status", report.getStatus().name()).append(",");
        appendNumberField(json, "resilienceScore", report.getResilienceScore()).append(",");
        appendNumberField(json, "duplicateOrderIds", report.getMetrics().getDuplicateOrderIds()).append(",");
        appendStringListField(json, "faultTargetOperations", experiment.getFaultTargetOperations().stream().toList()).append(",");
        appendStringListField(json, "failedInvariants", failedInvariants).append(",");
        appendPrometheusChecksField(json, "prometheusChecks", prometheusChecks).append(",");
        appendNumberField(json, "executionErrorCount", report.getExecutionErrors().size());
        json.append("}");

        try {
            Path parent = metadataPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(metadataPath, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write metadata to " + metadataPath, e);
        }
    }

    static String sha256(String value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static StringBuilder appendStringField(StringBuilder json, String name, String value) {
        json.append("\"").append(escape(name)).append("\":");
        json.append("\"").append(escape(value)).append("\"");
        return json;
    }

    private static StringBuilder appendNullableNumberField(StringBuilder json, String name, Number value) {
        json.append("\"").append(escape(name)).append("\":");
        if (value == null) {
            json.append("null");
        } else {
            json.append(value);
        }
        return json;
    }

    private static StringBuilder appendNumberField(StringBuilder json, String name, Number value) {
        json.append("\"").append(escape(name)).append("\":");
        json.append(value);
        return json;
    }

    private static StringBuilder appendBooleanField(StringBuilder json, String name, boolean value) {
        json.append("\"").append(escape(name)).append("\":");
        json.append(value);
        return json;
    }

    private static StringBuilder appendStringListField(StringBuilder json, String name, List<String> values) {
        json.append("\"").append(escape(name)).append("\":");
        json.append("[");
        for (int i = 0; i < values.size(); i++) {
            json.append("\"").append(escape(values.get(i))).append("\"");
            if (i < values.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json;
    }

    private static StringBuilder appendPrometheusChecksField(
            StringBuilder json,
            String name,
            List<PrometheusObservability.PrometheusCheckResult> checks
    ) {
        json.append("\"").append(escape(name)).append("\":");
        json.append("[");
        for (int i = 0; i < checks.size(); i++) {
            PrometheusObservability.PrometheusCheckResult checkResult = checks.get(i);
            PrometheusObservability.PrometheusCheckDefinition definition = checkResult.definition();
            json.append("{");
            appendStringField(json, "name", definition.name()).append(",");
            appendStringField(json, "query", definition.query()).append(",");
            appendStringField(json, "operator", definition.operator().symbol()).append(",");
            appendNumberField(json, "threshold", definition.threshold()).append(",");
            if (Double.isNaN(checkResult.actualValue())) {
                json.append("\"actualValue\":null,");
            } else {
                appendNumberField(json, "actualValue", checkResult.actualValue()).append(",");
            }
            appendBooleanField(json, "passed", checkResult.passed()).append(",");
            if (checkResult.error() == null) {
                appendStringField(json, "error", "");
            } else {
                appendStringField(json, "error", checkResult.error());
            }
            json.append("}");
            if (i < checks.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        return json;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }
}
