package com.chaosLab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ExperimentReportJson {

    private ExperimentReportJson() {
    }

    public static String toJson(ExperimentReport report) {
        Objects.requireNonNull(report, "report must not be null");

        StringBuilder json = new StringBuilder();
        json.append("{");
        appendStringField(json, "experimentName", report.getExperimentName()).append(",");
        appendStringField(json, "startedAt", report.getStartedAt().toString()).append(",");
        appendStringField(json, "finishedAt", report.getFinishedAt().toString()).append(",");
        appendStringField(json, "status", report.getStatus().name()).append(",");
        appendNumberField(json, "resilienceScore", report.getResilienceScore()).append(",");

        json.append("\"metrics\":");
        appendMetrics(json, report.getMetrics());
        json.append(",");

        json.append("\"phaseReports\":");
        appendPhaseReports(json, report.getPhaseReports());
        json.append(",");

        json.append("\"invariantResults\":");
        appendInvariantResults(json, report.getInvariantResults());
        json.append(",");

        json.append("\"executionErrors\":");
        appendExecutionErrors(json, report.getExecutionErrors());
        json.append("}");

        return json.toString();
    }

    public static void writeJson(ExperimentReport report, Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, toJson(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write report JSON to " + path, e);
        }
    }

    private static void appendMetrics(StringBuilder json, ExperimentMetrics metrics) {
        json.append("{");
        appendNumberField(json, "totalOperations", metrics.getTotalOperations()).append(",");
        appendNumberField(json, "successfulOperations", metrics.getSuccessfulOperations()).append(",");
        appendNumberField(json, "failedOperations", metrics.getFailedOperations()).append(",");
        appendNumberField(json, "errorRate", metrics.getErrorRate()).append(",");
        appendNumberField(json, "p95LatencyMs", metrics.getP95LatencyMs()).append(",");
        appendNumberField(json, "avgLatencyMs", metrics.getAvgLatencyMs()).append(",");
        appendNumberField(json, "maxLatencyMs", metrics.getMaxLatencyMs()).append(",");
        appendNumberField(json, "uniqueOrderIds", metrics.getUniqueOrderIds()).append(",");
        appendNumberField(json, "duplicateOrderIds", metrics.getDuplicateOrderIds());
        json.append("}");
    }

    private static void appendPhaseReports(StringBuilder json, List<PhaseReport> phaseReports) {
        json.append("[");
        for (int i = 0; i < phaseReports.size(); i++) {
            PhaseReport phaseReport = phaseReports.get(i);
            json.append("{");
            appendStringField(json, "phaseName", phaseReport.getPhaseName()).append(",");
            appendStringField(json, "phaseType", phaseReport.getPhaseType().name()).append(",");
            json.append("\"metrics\":");
            appendMetrics(json, phaseReport.getMetrics());
            json.append("}");
            if (i < phaseReports.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
    }

    private static void appendInvariantResults(StringBuilder json, List<InvariantResult> invariantResults) {
        json.append("[");
        for (int i = 0; i < invariantResults.size(); i++) {
            InvariantResult result = invariantResults.get(i);
            json.append("{");
            appendStringField(json, "name", result.getName()).append(",");
            appendBooleanField(json, "passed", result.isPassed()).append(",");
            appendStringField(json, "details", result.getDetails());
            json.append("}");
            if (i < invariantResults.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
    }

    private static void appendExecutionErrors(StringBuilder json, List<Throwable> executionErrors) {
        json.append("[");
        for (int i = 0; i < executionErrors.size(); i++) {
            Throwable error = executionErrors.get(i);
            json.append("{");
            appendStringField(json, "type", error.getClass().getName()).append(",");
            appendStringField(json, "message", error.getMessage() == null ? "" : error.getMessage());
            json.append("}");
            if (i < executionErrors.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
    }

    private static StringBuilder appendStringField(StringBuilder json, String name, String value) {
        json.append("\"").append(escape(name)).append("\":");
        json.append("\"").append(escape(value)).append("\"");
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
