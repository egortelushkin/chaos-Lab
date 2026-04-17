package com.chaosLab.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaosLab.InvariantResult;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class PrometheusObservability {

    private static final ObjectMapper JSON = new ObjectMapper();

    private PrometheusObservability() {
    }

    static PrometheusConfig parseFromYaml(String yamlText) {
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

        Map<String, Object> prometheus = getMap(observability, "prometheus", false);
        if (prometheus == null) {
            return null;
        }

        String baseUrl = normalizeBaseUrl(getString(prometheus, "baseUrl", true));
        long timeoutMs = getLong(prometheus, "timeoutMs", false, 5000L);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("observability.prometheus.timeoutMs must be > 0");
        }

        List<Map<String, Object>> checkMaps = getListOfMaps(prometheus, "checks", true);
        if (checkMaps.isEmpty()) {
            throw new IllegalArgumentException("observability.prometheus.checks must not be empty");
        }

        List<PrometheusCheckDefinition> checks = new ArrayList<>();
        for (Map<String, Object> checkMap : checkMaps) {
            String name = getString(checkMap, "name", true);
            String query = getString(checkMap, "query", true);
            String operatorRaw = getString(checkMap, "operator", false);
            if (operatorRaw == null) {
                operatorRaw = getString(checkMap, "op", false);
            }
            PrometheusOperator operator = PrometheusOperator.parse(operatorRaw == null ? "<=" : operatorRaw);
            double threshold = getDouble(checkMap, "threshold", true);

            String modeRaw = getString(checkMap, "mode", false);
            if (modeRaw == null) {
                modeRaw = getString(checkMap, "queryType", false);
            }
            PrometheusQueryMode mode = PrometheusQueryMode.parse(modeRaw == null ? "instant" : modeRaw);

            long rangeSeconds = getLong(checkMap, "rangeSeconds", false, 300L);
            if (!checkMap.containsKey("rangeSeconds")) {
                rangeSeconds = getLong(checkMap, "windowSeconds", false, rangeSeconds);
            }
            if (!checkMap.containsKey("rangeSeconds") && !checkMap.containsKey("windowSeconds")) {
                rangeSeconds = getLong(checkMap, "lookbackSeconds", false, rangeSeconds);
            }
            long stepSeconds = getLong(checkMap, "stepSeconds", false, 15L);
            String reducerRaw = getString(checkMap, "reducer", false);
            if (reducerRaw == null) {
                reducerRaw = getString(checkMap, "aggregation", false);
            }
            PrometheusReducer reducer = PrometheusReducer.parse(reducerRaw == null ? "max" : reducerRaw);

            if (mode == PrometheusQueryMode.RANGE) {
                if (rangeSeconds <= 0) {
                    throw new IllegalArgumentException("observability.prometheus.checks.rangeSeconds must be > 0 for range mode");
                }
                if (stepSeconds <= 0) {
                    throw new IllegalArgumentException("observability.prometheus.checks.stepSeconds must be > 0 for range mode");
                }
            }

            checks.add(new PrometheusCheckDefinition(
                    name,
                    query,
                    operator,
                    threshold,
                    mode,
                    rangeSeconds,
                    stepSeconds,
                    reducer
            ));
        }

        return new PrometheusConfig(baseUrl, timeoutMs, List.copyOf(checks));
    }

    static List<PrometheusCheckResult> evaluate(PrometheusConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs()))
                .build();

        List<PrometheusCheckResult> results = new ArrayList<>();
        for (PrometheusCheckDefinition check : config.checks()) {
            results.add(evaluateOne(client, config, check));
        }
        return List.copyOf(results);
    }

    static InvariantResult toInvariantResult(PrometheusCheckResult result) {
        PrometheusCheckDefinition check = result.definition();
        String name = "prometheus." + check.name() + " " + check.operator().symbol() + " " + check.threshold();
        if (result.error() != null) {
            return new InvariantResult(
                    name,
                    false,
                    "error=" + result.error() + ", query=" + check.query() + ", mode=" + check.mode().wireName()
            );
        }
        return new InvariantResult(
                name,
                result.passed(),
                "actual=" + result.actualValue() + ", query=" + check.query() + ", mode=" + check.mode().wireName() + ", reducer=" + check.reducer().wireName()
        );
    }

    private static PrometheusCheckResult evaluateOne(
            HttpClient client,
            PrometheusConfig config,
            PrometheusCheckDefinition check
    ) {
        try {
            String encodedQuery = URLEncoder.encode(check.query(), StandardCharsets.UTF_8);
            long end = Instant.now().getEpochSecond();
            URI uri;
            if (check.mode() == PrometheusQueryMode.RANGE) {
                long start = end - check.rangeSeconds();
                uri = URI.create(
                        config.baseUrl() +
                                "/api/v1/query_range?query=" + encodedQuery +
                                "&start=" + start +
                                "&end=" + end +
                                "&step=" + check.stepSeconds()
                );
            } else {
                uri = URI.create(config.baseUrl() + "/api/v1/query?query=" + encodedQuery + "&time=" + end);
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new PrometheusCheckResult(check, Double.NaN, false, "HTTP " + response.statusCode());
            }

            JsonNode root = JSON.readTree(response.body());
            String status = root.path("status").asText("");
            if (!"success".equals(status)) {
                return new PrometheusCheckResult(check, Double.NaN, false, "Prometheus status=" + status);
            }

            JsonNode data = root.path("data");
            String resultType = data.path("resultType").asText("");
            List<PrometheusSample> samples = extractSamples(data.path("result"), resultType);
            double actual = reduceSamples(samples, check.reducer());

            if (Double.isNaN(actual)) {
                return new PrometheusCheckResult(check, Double.NaN, false, "No numeric value in result");
            }

            boolean passed = check.operator().test(actual, check.threshold());
            return new PrometheusCheckResult(check, actual, passed, null);
        } catch (Exception e) {
            return new PrometheusCheckResult(check, Double.NaN, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static List<PrometheusSample> extractSamples(JsonNode resultNode, String resultType) {
        List<PrometheusSample> samples = new ArrayList<>();
        switch (resultType) {
            case "vector" -> extractVectorSamples(resultNode, samples);
            case "matrix" -> extractMatrixSamples(resultNode, samples);
            case "scalar" -> extractScalarSamples(resultNode, samples);
            default -> {
                return List.of();
            }
        }
        return samples;
    }

    private static void extractVectorSamples(JsonNode vectorNode, List<PrometheusSample> out) {
        if (!vectorNode.isArray()) {
            return;
        }
        for (JsonNode item : vectorNode) {
            appendSampleFromPair(item.path("value"), out);
        }
    }

    private static void extractMatrixSamples(JsonNode matrixNode, List<PrometheusSample> out) {
        if (!matrixNode.isArray()) {
            return;
        }
        for (JsonNode series : matrixNode) {
            JsonNode values = series.path("values");
            if (!values.isArray()) {
                continue;
            }
            for (JsonNode pair : values) {
                appendSampleFromPair(pair, out);
            }
        }
    }

    private static void extractScalarSamples(JsonNode scalarNode, List<PrometheusSample> out) {
        appendSampleFromPair(scalarNode, out);
    }

    private static void appendSampleFromPair(JsonNode pairNode, List<PrometheusSample> out) {
        if (!pairNode.isArray() || pairNode.size() < 2) {
            return;
        }
        double value = parseNumericNode(pairNode.get(1));
        if (Double.isNaN(value)) {
            return;
        }
        long timestamp = parseEpochSecondsNode(pairNode.get(0));
        out.add(new PrometheusSample(timestamp, value));
    }

    private static double reduceSamples(List<PrometheusSample> samples, PrometheusReducer reducer) {
        if (samples.isEmpty()) {
            return Double.NaN;
        }
        return switch (reducer) {
            case MAX -> samples.stream().mapToDouble(PrometheusSample::value).max().orElse(Double.NaN);
            case MIN -> samples.stream().mapToDouble(PrometheusSample::value).min().orElse(Double.NaN);
            case AVG -> samples.stream().mapToDouble(PrometheusSample::value).average().orElse(Double.NaN);
            case LAST -> samples.stream()
                    .max(Comparator.comparingLong(PrometheusSample::timestampSeconds))
                    .map(PrometheusSample::value)
                    .orElse(Double.NaN);
        };
    }

    private static long parseEpochSecondsNode(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return 0L;
        }
        if (valueNode.isNumber()) {
            return valueNode.asLong();
        }
        try {
            return (long) Double.parseDouble(valueNode.asText());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double parseNumericNode(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return Double.NaN;
        }
        if (valueNode.isNumber()) {
            return valueNode.asDouble();
        }
        String raw = valueNode.asText();
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return Double.NaN;
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getListOfMaps(Map<String, Object> map, String key, boolean required) {
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

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : rawList) {
            if (!(element instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("List '" + key + "' must contain objects");
            }
            result.add((Map<String, Object>) rawMap);
        }
        return result;
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

    private static double getDouble(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required double: " + key);
            }
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    record PrometheusConfig(String baseUrl, long timeoutMs, List<PrometheusCheckDefinition> checks) {
        PrometheusConfig {
            Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            Objects.requireNonNull(checks, "checks must not be null");
        }
    }

    record PrometheusCheckDefinition(
            String name,
            String query,
            PrometheusOperator operator,
            double threshold,
            PrometheusQueryMode mode,
            long rangeSeconds,
            long stepSeconds,
            PrometheusReducer reducer
    ) {
        PrometheusCheckDefinition {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(query, "query must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(mode, "mode must not be null");
            Objects.requireNonNull(reducer, "reducer must not be null");
        }
    }

    record PrometheusCheckResult(
            PrometheusCheckDefinition definition,
            double actualValue,
            boolean passed,
            String error
    ) {
        PrometheusCheckResult {
            Objects.requireNonNull(definition, "definition must not be null");
        }
    }

    record PrometheusSample(long timestampSeconds, double value) {
    }

    enum PrometheusQueryMode {
        INSTANT("instant"),
        RANGE("range");

        private final String wireName;

        PrometheusQueryMode(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }

        static PrometheusQueryMode parse(String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "instant", "query" -> INSTANT;
                case "range", "query_range" -> RANGE;
                default -> throw new IllegalArgumentException("Unknown prometheus query mode: " + raw);
            };
        }
    }

    enum PrometheusReducer {
        MAX("max"),
        MIN("min"),
        AVG("avg"),
        LAST("last");

        private final String wireName;

        PrometheusReducer(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }

        static PrometheusReducer parse(String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "max" -> MAX;
                case "min" -> MIN;
                case "avg", "average", "mean" -> AVG;
                case "last", "latest" -> LAST;
                default -> throw new IllegalArgumentException("Unknown prometheus reducer: " + raw);
            };
        }
    }

    enum PrometheusOperator {
        LT("<") {
            @Override
            boolean test(double actual, double threshold) {
                return actual < threshold;
            }
        },
        LE("<=") {
            @Override
            boolean test(double actual, double threshold) {
                return actual <= threshold;
            }
        },
        GT(">") {
            @Override
            boolean test(double actual, double threshold) {
                return actual > threshold;
            }
        },
        GE(">=") {
            @Override
            boolean test(double actual, double threshold) {
                return actual >= threshold;
            }
        },
        EQ("==") {
            @Override
            boolean test(double actual, double threshold) {
                return Double.compare(actual, threshold) == 0;
            }
        },
        NE("!=") {
            @Override
            boolean test(double actual, double threshold) {
                return Double.compare(actual, threshold) != 0;
            }
        };

        private final String symbol;

        PrometheusOperator(String symbol) {
            this.symbol = symbol;
        }

        String symbol() {
            return symbol;
        }

        abstract boolean test(double actual, double threshold);

        static PrometheusOperator parse(String raw) {
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "<" -> LT;
                case "<=", "le", "lte" -> LE;
                case ">" -> GT;
                case ">=", "ge", "gte" -> GE;
                case "==", "=" -> EQ;
                case "!=", "<>", "ne" -> NE;
                default -> throw new IllegalArgumentException("Unknown prometheus operator: " + raw);
            };
        }
    }
}
