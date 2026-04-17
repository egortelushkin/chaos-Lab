package com.chaosLab.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ExperimentPhase;
import com.chaosLab.ExperimentReport;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class GrafanaObservability {

    private static final ObjectMapper JSON = new ObjectMapper();

    private GrafanaObservability() {
    }

    static GrafanaConfig parseFromYaml(String yamlText) {
        Objects.requireNonNull(yamlText, "yamlText must not be null");

        Object loaded = new Yaml().load(new StringReader(yamlText));
        if (!(loaded instanceof Map<?, ?> rootRaw)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootRaw;
        Map<String, Object> observability = getMap(root, "observability", false);
        if (observability == null) {
            return null;
        }

        Map<String, Object> grafana = getMap(observability, "grafana", false);
        if (grafana == null) {
            return null;
        }

        boolean enabled = getBoolean(grafana, "enabled", false, true);
        if (!enabled) {
            return null;
        }

        String baseUrl = normalizeBaseUrl(getString(grafana, "baseUrl", true));
        long timeoutMs = getLong(grafana, "timeoutMs", false, 5000L);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("observability.grafana.timeoutMs must be > 0");
        }

        String apiToken = getString(grafana, "apiToken", false);
        String apiTokenEnv = getString(grafana, "apiTokenEnv", false);
        if ((apiToken == null || apiToken.isBlank()) && apiTokenEnv != null && !apiTokenEnv.isBlank()) {
            apiToken = System.getenv(apiTokenEnv);
        }
        if (apiToken != null && apiToken.isBlank()) {
            apiToken = null;
        }

        String dashboardUid = getString(grafana, "dashboardUid", false);
        Integer panelId = getInteger(grafana, "panelId", false);
        List<String> tags = getListOfStrings(grafana, "tags", false);
        if (tags.isEmpty()) {
            tags = List.of("chaoslib");
        }

        boolean annotateRun = getBoolean(grafana, "annotateRun", false, true);
        boolean annotatePhases = getBoolean(grafana, "annotatePhases", false, true);
        boolean failOnError = getBoolean(grafana, "failOnError", false, false);

        return new GrafanaConfig(
                baseUrl,
                timeoutMs,
                apiToken,
                dashboardUid,
                panelId,
                List.copyOf(tags),
                annotateRun,
                annotatePhases,
                failOnError
        );
    }

    static GrafanaPublishResult publish(
            GrafanaConfig config,
            ChaosExperiment experiment,
            ExperimentReport report
    ) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(experiment, "experiment must not be null");
        Objects.requireNonNull(report, "report must not be null");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs()))
                .build();

        List<Long> createdIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int requested = 0;

        if (config.annotatePhases()) {
            long cursor = report.getStartedAt().toEpochMilli();
            for (ExperimentPhase phase : experiment.getPhases()) {
                long phaseStart = cursor;
                long phaseEnd = phaseStart + phase.getDuration().toMillis();
                cursor = phaseEnd;

                List<String> tags = new ArrayList<>(config.tags());
                tags.add("phase:" + phase.getType().name().toLowerCase());
                tags.add("phase_name:" + phase.getName());

                requested++;
                postAnnotation(
                        client,
                        config,
                        phaseStart,
                        phaseEnd,
                        "Chaos phase: " + phase.getName() + " (" + phase.getType().name() + ")",
                        tags,
                        createdIds,
                        errors
                );
            }
        }

        if (config.annotateRun()) {
            List<String> tags = new ArrayList<>(config.tags());
            tags.add("experiment:" + report.getExperimentName());
            tags.add("status:" + report.getStatus().name().toLowerCase());

            requested++;
            postAnnotation(
                    client,
                    config,
                    report.getStartedAt().toEpochMilli(),
                    report.getFinishedAt().toEpochMilli(),
                    "Chaos run: " + report.getExperimentName() + ", status=" + report.getStatus().name() + ", score=" + report.getResilienceScore(),
                    tags,
                    createdIds,
                    errors
            );
        }

        return new GrafanaPublishResult(requested, List.copyOf(createdIds), List.copyOf(errors));
    }

    private static void postAnnotation(
            HttpClient client,
            GrafanaConfig config,
            long timeMs,
            long timeEndMs,
            String text,
            List<String> tags,
            List<Long> createdIds,
            List<String> errors
    ) {
        try {
            ObjectNode payload = JSON.createObjectNode();
            payload.put("time", timeMs);
            payload.put("timeEnd", timeEndMs);
            payload.put("text", text);
            if (config.dashboardUid() != null) {
                payload.put("dashboardUID", config.dashboardUid());
            }
            if (config.panelId() != null) {
                payload.put("panelId", config.panelId());
            }
            var tagsNode = payload.putArray("tags");
            for (String tag : tags) {
                tagsNode.add(tag);
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/api/annotations"))
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

            if (config.apiToken() != null) {
                requestBuilder.header("Authorization", "Bearer " + config.apiToken());
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                errors.add("HTTP " + response.statusCode() + " for annotation '" + text + "'");
                return;
            }

            JsonNode body = JSON.readTree(response.body());
            JsonNode idNode = body.path("id");
            if (idNode.isNumber()) {
                createdIds.add(idNode.asLong());
            }
        } catch (Exception e) {
            errors.add(e.getClass().getSimpleName() + ": " + e.getMessage() + " for annotation '" + text + "'");
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required object: " + key);
            }
            return null;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Expected object at key: " + key);
        }
        return (Map<String, Object>) rawMap;
    }

    private static String getString(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required string: " + key);
            }
            return null;
        }
        String parsed = String.valueOf(value).trim();
        if (required && parsed.isEmpty()) {
            throw new IllegalArgumentException("Missing required string: " + key);
        }
        return parsed;
    }

    private static Integer getInteger(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required int: " + key);
            }
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static long getLong(Map<String, Object> map, String key, boolean required, long defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required long: " + key);
            }
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> getListOfStrings(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required list: " + key);
            }
            return List.of();
        }
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("Expected list at key: " + key);
        }
        List<String> result = new ArrayList<>();
        for (Object element : rawList) {
            if (element == null) {
                continue;
            }
            String parsed = String.valueOf(element).trim();
            if (!parsed.isEmpty()) {
                result.add(parsed);
            }
        }
        return result;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean required, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required boolean: " + key);
            }
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String raw = String.valueOf(value).trim().toLowerCase();
        return switch (raw) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException("Expected boolean at key: " + key);
        };
    }

    record GrafanaConfig(
            String baseUrl,
            long timeoutMs,
            String apiToken,
            String dashboardUid,
            Integer panelId,
            List<String> tags,
            boolean annotateRun,
            boolean annotatePhases,
            boolean failOnError
    ) {
        GrafanaConfig {
            Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            Objects.requireNonNull(tags, "tags must not be null");
        }
    }

    record GrafanaPublishResult(
            int requested,
            List<Long> annotationIds,
            List<String> errors
    ) {
        GrafanaPublishResult {
            Objects.requireNonNull(annotationIds, "annotationIds must not be null");
            Objects.requireNonNull(errors, "errors must not be null");
        }
    }
}
