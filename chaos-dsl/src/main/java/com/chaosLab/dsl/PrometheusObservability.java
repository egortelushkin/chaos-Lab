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
            checks.add(new PrometheusCheckDefinition(name, query, operator, threshold));
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
            return new InvariantResult(name, false, "error=" + result.error() + ", query=" + check.query());
        }
        return new InvariantResult(name, result.passed(), "actual=" + result.actualValue() + ", query=" + check.query());
    }

    private static PrometheusCheckResult evaluateOne(
            HttpClient client,
            PrometheusConfig config,
            PrometheusCheckDefinition check
    ) {
        try {
            String encodedQuery = URLEncoder.encode(check.query(), StandardCharsets.UTF_8);
            long time = Instant.now().getEpochSecond();
            URI uri = URI.create(config.baseUrl() + "/api/v1/query?query=" + encodedQuery + "&time=" + time);

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
            double actual;
            if ("vector".equals(resultType)) {
                actual = extractVectorValue(data.path("result"));
            } else if ("scalar".equals(resultType)) {
                actual = extractScalarValue(data.path("result"));
            } else {
                return new PrometheusCheckResult(check, Double.NaN, false, "Unsupported resultType=" + resultType);
            }

            if (Double.isNaN(actual)) {
                return new PrometheusCheckResult(check, Double.NaN, false, "No numeric value in result");
            }

            boolean passed = check.operator().test(actual, check.threshold());
            return new PrometheusCheckResult(check, actual, passed, null);
        } catch (Exception e) {
            return new PrometheusCheckResult(check, Double.NaN, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static double extractVectorValue(JsonNode vectorNode) {
        if (!vectorNode.isArray() || vectorNode.isEmpty()) {
            return Double.NaN;
        }

        double max = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (JsonNode item : vectorNode) {
            JsonNode value = item.path("value");
            if (!value.isArray() || value.size() < 2) {
                continue;
            }
            double parsed = parseNumericNode(value.get(1));
            if (Double.isNaN(parsed)) {
                continue;
            }
            found = true;
            if (parsed > max) {
                max = parsed;
            }
        }
        return found ? max : Double.NaN;
    }

    private static double extractScalarValue(JsonNode scalarNode) {
        if (!scalarNode.isArray() || scalarNode.size() < 2) {
            return Double.NaN;
        }
        return parseNumericNode(scalarNode.get(1));
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
            double threshold
    ) {
        PrometheusCheckDefinition {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(query, "query must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
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
