package com.chaosLab.dsl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chaosLab.Chaos;
import com.chaosLab.ChaosBuilder;
import com.chaosLab.ChaosEngine;
import com.chaosLab.ChaosExecutionMode;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ChaosExperimentBuilder;
import com.chaosLab.PhaseType;
import com.chaosLab.StepResult;
import com.chaosLab.StepSequenceUser;
import com.chaosLab.UserSession;
import com.chaosLab.UserStep;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChaosDsl {

    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\$\\{([^}]+)}");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private ChaosDsl() {
    }

    public static ChaosExperiment fromFile(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try (InputStream input = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(input);
            return fromRawYaml(loaded);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read DSL file: " + path, e);
        }
    }

    public static ChaosExperiment fromYaml(String yamlText) {
        Objects.requireNonNull(yamlText, "yamlText must not be null");
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(new StringReader(yamlText));
        return fromRawYaml(loaded);
    }

    @SuppressWarnings("unchecked")
    private static ChaosExperiment fromRawYaml(Object loaded) {
        if (!(loaded instanceof Map<?, ?> rootRaw)) {
            throw new IllegalArgumentException("DSL root must be a map");
        }

        Map<String, Object> root = (Map<String, Object>) rootRaw;
        Map<String, Object> experimentMap = getMap(root, "experiment", true);
        String experimentName = getString(experimentMap, "name", true);
        ChaosExperimentBuilder builder = Chaos.experiment(experimentName);

        if (experimentMap.containsKey("seed")) {
            builder.withSeed(getLong(experimentMap, "seed", true));
        }

        Map<String, Object> loadMap = getMap(root, "load", false);
        if (loadMap != null) {
            if (loadMap.containsKey("virtualUsers")) {
                builder.virtualUsers(getInt(loadMap, "virtualUsers", true));
            }
            if (loadMap.containsKey("workerThreads")) {
                builder.workerThreads(getInt(loadMap, "workerThreads", true));
            }
            if (loadMap.containsKey("thinkTimeMs")) {
                builder.thinkTime(Duration.ofMillis(getLong(loadMap, "thinkTimeMs", true)));
            }
        }

        List<Map<String, Object>> phases = getListOfMaps(root, "phases", true);
        for (Map<String, Object> phaseMap : phases) {
            String phaseName = getString(phaseMap, "name", true);
            String phaseTypeRaw = getString(phaseMap, "type", true);
            PhaseType phaseType = parsePhaseType(phaseTypeRaw);
            long durationMs = getLong(phaseMap, "durationMs", true);
            builder.phase(phaseName, phaseType, Duration.ofMillis(durationMs));
        }

        Map<String, Object> faultMap = getMap(root, "fault", false);
        if (faultMap != null) {
            builder.faultEngine(parseFaultEngine(faultMap));
            List<String> targetOperations = getListOfStrings(faultMap, "targetOperations", false);
            if (!targetOperations.isEmpty()) {
                builder.faultTargetOperations(targetOperations);
            }
        }

        Map<String, Object> invariantMap = getMap(root, "invariants", false);
        if (invariantMap != null) {
            if (invariantMap.containsKey("maxErrorRate")) {
                builder.maxErrorRate(getDouble(invariantMap, "maxErrorRate", true));
            }
            if (invariantMap.containsKey("maxP95LatencyMs")) {
                builder.maxP95LatencyMs(getDouble(invariantMap, "maxP95LatencyMs", true));
            }
            if (getBoolean(invariantMap, "noDuplicateOrderIds", false, false)) {
                builder.noDuplicateOrderIds();
            }
        }

        builder.users(() -> buildConfiguredUser(root));
        return builder.build();
    }

    private static ChaosEngine parseFaultEngine(Map<String, Object> faultMap) {
        ChaosBuilder chaosBuilder = Chaos.builder();

        if (faultMap.containsKey("delayMaxMs")) {
            int delayMaxMs = getInt(faultMap, "delayMaxMs", true);
            double delayProbability = getDouble(faultMap, "delayProbability", false, 1.0);
            chaosBuilder.delay(delayMaxMs).probability(delayProbability);
        }

        if (faultMap.containsKey("exceptionProbability")) {
            double exceptionProbability = getDouble(faultMap, "exceptionProbability", true);
            chaosBuilder.exception().probability(exceptionProbability);
        }

        if (faultMap.containsKey("mode")) {
            String mode = getString(faultMap, "mode", true).toUpperCase(Locale.ROOT);
            chaosBuilder.executionMode(ChaosExecutionMode.valueOf(mode));
        }

        if (faultMap.containsKey("seed")) {
            chaosBuilder.withSeed(getLong(faultMap, "seed", true));
        }

        return chaosBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private static StepSequenceUser buildConfiguredUser(Map<String, Object> root) {
        Map<String, Object> usersMap = getMap(root, "users", true);
        List<Map<String, Object>> steps = getListOfMaps(usersMap, "steps", true);

        List<UserStep> userSteps = new ArrayList<>();
        HttpClient httpClient = HttpClient.newBuilder().build();
        for (Map<String, Object> stepMap : steps) {
            String operation = getString(stepMap, "operation", true);
            String httpMethod = getString(stepMap, "method", false);
            String httpUrl = getString(stepMap, "url", false);
            long latencyMs = getLong(stepMap, "latencyMs", false, 0L);
            double successRate = getDouble(stepMap, "successRate", false, 1.0);
            boolean emitOrderId = getBoolean(stepMap, "emitOrderId", false, false);
            double duplicateOrderIdRate = getDouble(stepMap, "duplicateOrderIdRate", false, 0.0);
            String requestBody = getString(stepMap, "body", false);
            Map<String, String> headers = getStringMap(stepMap, "headers", false);
            Set<Integer> successStatusCodes = getSetOfInts(stepMap, "successStatusCodes", false);
            Map<String, String> captureMap = getStringMap(stepMap, "capture", false);
            String emitOrderIdFromSession = getString(stepMap, "emitOrderIdFromSession", false);
            String emitOrderIdJsonField = getString(stepMap, "emitOrderIdJsonField", false);
            long timeoutMs = getLong(stepMap, "timeoutMs", false, 5000L);
            if (Double.isNaN(successRate) || successRate < 0.0 || successRate > 1.0) {
                throw new IllegalArgumentException("users.steps.successRate must be in [0..1]");
            }
            if (Double.isNaN(duplicateOrderIdRate) || duplicateOrderIdRate < 0.0 || duplicateOrderIdRate > 1.0) {
                throw new IllegalArgumentException("users.steps.duplicateOrderIdRate must be in [0..1]");
            }
            if (duplicateOrderIdRate > 0.0 && !emitOrderId) {
                throw new IllegalArgumentException("users.steps.duplicateOrderIdRate requires emitOrderId=true");
            }
            if ((httpMethod == null) != (httpUrl == null)) {
                throw new IllegalArgumentException("users.steps.method and users.steps.url must be configured together");
            }
            if (httpMethod != null && (emitOrderId || duplicateOrderIdRate > 0.0)) {
                throw new IllegalArgumentException("users.steps.emitOrderId/duplicateOrderIdRate are simulation-only and not allowed for HTTP steps");
            }
            if (httpMethod != null && timeoutMs <= 0) {
                throw new IllegalArgumentException("users.steps.timeoutMs must be > 0");
            }

            if (httpMethod != null) {
                userSteps.add(UserStep.named(operation, session -> executeHttpStep(
                        session,
                        operation,
                        httpClient,
                        httpMethod,
                        httpUrl,
                        requestBody,
                        headers,
                        successStatusCodes,
                        captureMap,
                        emitOrderIdFromSession,
                        emitOrderIdJsonField,
                        timeoutMs
                )));
            } else {
                userSteps.add(UserStep.named(operation, session -> executeSyntheticStep(
                        session,
                        operation,
                        latencyMs,
                        successRate,
                        emitOrderId,
                        duplicateOrderIdRate
                )));
            }
        }

        return new StepSequenceUser(userSteps);
    }

    private static StepResult executeHttpStep(
            UserSession session,
            String operation,
            HttpClient httpClient,
            String httpMethod,
            String httpUrl,
            String requestBody,
            Map<String, String> headers,
            Set<Integer> successStatusCodes,
            Map<String, String> captureMap,
            String emitOrderIdFromSession,
            String emitOrderIdJsonField,
            long timeoutMs
    ) {
        try {
            String resolvedUrl = resolveTemplate(httpUrl, session);
            String resolvedBody = requestBody == null ? null : resolveTemplate(requestBody, session);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(resolvedUrl))
                    .timeout(Duration.ofMillis(timeoutMs));

            if (resolvedBody == null) {
                requestBuilder.method(httpMethod.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.noBody());
            } else {
                requestBuilder.method(httpMethod.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.ofString(resolvedBody));
            }

            for (Map.Entry<String, String> header : headers.entrySet()) {
                String value = resolveTemplate(header.getValue(), session);
                requestBuilder.header(header.getKey(), value);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body() == null ? "" : response.body();
            JsonNode responseJson = (captureMap.isEmpty() && emitOrderIdJsonField == null)
                    ? null
                    : parseJsonBody(responseBody);
            boolean success = successStatusCodes.isEmpty()
                    ? statusCode >= 200 && statusCode < 300
                    : successStatusCodes.contains(statusCode);

            if (!success) {
                return StepResult.failure(operation);
            }

            for (Map.Entry<String, String> captureEntry : captureMap.entrySet()) {
                String sessionKey = captureEntry.getKey();
                String jsonField = captureEntry.getValue();
                String captured = extractJsonField(responseJson, jsonField);
                if (captured != null) {
                    session.putAttribute(sessionKey, captured);
                }
            }

            String orderId = null;
            if (emitOrderIdFromSession != null) {
                Object attribute = session.getAttribute(emitOrderIdFromSession);
                if (attribute != null) {
                    orderId = String.valueOf(attribute);
                }
            } else if (emitOrderIdJsonField != null) {
                orderId = extractJsonField(responseJson, emitOrderIdJsonField);
            }

            if (orderId != null && !orderId.isBlank()) {
                return StepResult.successWithOrderId(operation, orderId);
            }
            return StepResult.success(operation);
        } catch (Exception e) {
            return StepResult.failure(operation);
        }
    }

    private static StepResult executeSyntheticStep(
            UserSession session,
            String operation,
            long latencyMs,
            double successRate,
            boolean emitOrderId,
            double duplicateOrderIdRate
    ) throws Exception {
        if (latencyMs > 0) {
            Thread.sleep(latencyMs);
        }

        String randomKey = "dsl.random";
        Random random = session.getAttribute(randomKey, Random.class);
        if (random == null) {
            random = new Random(session.getSeed());
            session.putAttribute(randomKey, random);
        }

        if (random.nextDouble() > successRate) {
            return StepResult.failure(operation);
        }

        if (!emitOrderId) {
            return StepResult.success(operation);
        }

        String lastOrderIdKey = "dsl.order.last." + operation;
        String lastOrderId = session.getAttribute(lastOrderIdKey, String.class);
        boolean reuseOrderId = lastOrderId != null && random.nextDouble() < duplicateOrderIdRate;
        if (reuseOrderId) {
            return StepResult.successWithOrderId(operation, lastOrderId);
        }

        String counterKey = "dsl.order.counter." + operation;
        Long counter = session.getAttribute(counterKey, Long.class);
        long nextCounter = counter == null ? 1L : counter + 1L;
        session.putAttribute(counterKey, nextCounter);
        String generatedOrderId = operation + "-" + session.getUserId() + "-" + nextCounter;
        session.putAttribute(lastOrderIdKey, generatedOrderId);
        return StepResult.successWithOrderId(operation, generatedOrderId);
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
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Expected object at key: " + key);
        }
        return (Map<String, Object>) value;
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

    private static List<String> getListOfStrings(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required list: " + key);
            }
            return List.of();
        }

        if (value instanceof List<?> rawList) {
            List<String> result = new ArrayList<>();
            for (Object element : rawList) {
                if (element == null) {
                    throw new IllegalArgumentException("List '" + key + "' must not contain null");
                }
                String normalized = String.valueOf(element).trim();
                if (normalized.isEmpty()) {
                    throw new IllegalArgumentException("List '" + key + "' must not contain blank values");
                }
                result.add(normalized);
            }
            return result;
        }

        String singleValue = String.valueOf(value).trim();
        if (singleValue.isEmpty()) {
            throw new IllegalArgumentException("Expected non-empty value at key: " + key);
        }
        return List.of(singleValue);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getStringMap(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required map: " + key);
            }
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Expected map at key: " + key);
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Map '" + key + "' must not contain null key/value");
            }
            String mapKey = String.valueOf(entry.getKey()).trim();
            String mapValue = String.valueOf(entry.getValue()).trim();
            if (mapKey.isEmpty() || mapValue.isEmpty()) {
                throw new IllegalArgumentException("Map '" + key + "' must not contain blank key/value");
            }
            result.put(mapKey, mapValue);
        }
        return result;
    }

    private static Set<Integer> getSetOfInts(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required list: " + key);
            }
            return Set.of();
        }
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalArgumentException("Expected list at key: " + key);
        }

        Set<Integer> result = new HashSet<>();
        for (Object item : rawList) {
            if (item == null) {
                throw new IllegalArgumentException("List '" + key + "' must not contain null");
            }
            if (item instanceof Number number) {
                result.add(number.intValue());
            } else {
                result.add(Integer.parseInt(String.valueOf(item)));
            }
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
        return String.valueOf(value);
    }

    private static int getInt(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required int: " + key);
            }
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static long getLong(Map<String, Object> map, String key, boolean required) {
        return getLong(map, key, required, 0L);
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
        return getDouble(map, key, required, 0.0);
    }

    private static double getDouble(Map<String, Object> map, String key, boolean required, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("Missing required double: " + key);
            }
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
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
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException("Expected boolean at key: " + key + ", got: " + value);
        };
    }

    private static String resolveTemplate(String template, UserSession session) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String sessionKey = matcher.group(1).trim();
            Object value = session.getAttribute(sessionKey);
            if (value == null) {
                throw new IllegalStateException("Missing session attribute: " + sessionKey);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static JsonNode parseJsonBody(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return null;
        }
        try {
            return JSON_MAPPER.readTree(jsonBody);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("HTTP response is not valid JSON", e);
        }
    }

    private static String extractJsonField(JsonNode jsonBody, String pathExpression) {
        if (jsonBody == null || pathExpression == null || pathExpression.isBlank()) {
            return null;
        }

        JsonNode resolved = jsonBody.at(resolvePathOrPointer(pathExpression.trim()));
        if (resolved.isMissingNode() || resolved.isNull()) {
            return null;
        }
        if (resolved.isValueNode()) {
            return resolved.asText();
        }
        return resolved.toString();
    }

    private static String[] splitPathSegments(String pathExpression) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (int i = 0; i < pathExpression.length(); i++) {
            char ch = pathExpression.charAt(i);
            if (ch == '.' && bracketDepth == 0) {
                segments.add(current.toString());
                current.setLength(0);
                continue;
            }
            if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']') {
                bracketDepth--;
                if (bracketDepth < 0) {
                    throw new IllegalArgumentException("Invalid JSON path expression: " + pathExpression);
                }
            }
            current.append(ch);
        }

        if (bracketDepth != 0) {
            throw new IllegalArgumentException("Invalid JSON path expression: " + pathExpression);
        }

        if (!current.isEmpty()) {
            segments.add(current.toString());
        }

        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Invalid JSON path expression: " + pathExpression);
        }
        return segments.toArray(String[]::new);
    }

    private static String resolvePathOrPointer(String pathExpression) {
        if (pathExpression.startsWith("/")) {
            return pathExpression;
        }

        String[] segments = splitPathSegments(pathExpression);
        StringBuilder pointer = new StringBuilder();
        for (String segment : segments) {
            appendPointerTokens(pointer, segment, pathExpression);
        }
        return pointer.toString();
    }

    private static void appendPointerTokens(StringBuilder pointer, String segment, String fullExpression) {
        if (segment.isBlank()) {
            throw new IllegalArgumentException("Invalid JSON path expression: " + fullExpression);
        }

        int index = 0;
        if (!segment.startsWith("[")) {
            int bracketIndex = segment.indexOf('[');
            String fieldName = bracketIndex < 0 ? segment : segment.substring(0, bracketIndex);
            if (fieldName.isBlank()) {
                throw new IllegalArgumentException("Invalid JSON path expression: " + fullExpression);
            }
            pointer.append('/').append(escapeJsonPointerToken(fieldName));
            index = bracketIndex < 0 ? segment.length() : bracketIndex;
        }

        while (index < segment.length()) {
            if (segment.charAt(index) != '[') {
                throw new IllegalArgumentException("Invalid JSON path expression: " + fullExpression);
            }
            int closeIndex = segment.indexOf(']', index);
            if (closeIndex < 0) {
                throw new IllegalArgumentException("Invalid JSON path expression: " + fullExpression);
            }
            String indexToken = segment.substring(index + 1, closeIndex).trim();
            if (indexToken.isEmpty()) {
                throw new IllegalArgumentException("Invalid JSON path expression: " + fullExpression);
            }
            try {
                Integer.parseInt(indexToken);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid array index in JSON path expression: " + fullExpression, e);
            }
            pointer.append('/').append(indexToken);
            index = closeIndex + 1;
        }
    }

    private static String escapeJsonPointerToken(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static PhaseType parsePhaseType(String raw) {
        try {
            return PhaseType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown phase type: " + raw, e);
        }
    }
}
