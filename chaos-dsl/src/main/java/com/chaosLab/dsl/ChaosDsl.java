package com.chaosLab.dsl;

import com.chaosLab.Chaos;
import com.chaosLab.ChaosBuilder;
import com.chaosLab.ChaosEngine;
import com.chaosLab.ChaosExecutionMode;
import com.chaosLab.ChaosExperiment;
import com.chaosLab.ChaosExperimentBuilder;
import com.chaosLab.PhaseType;
import com.chaosLab.StepResult;
import com.chaosLab.StepSequenceUser;
import com.chaosLab.UserStep;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class ChaosDsl {

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
        }

        Map<String, Object> invariantMap = getMap(root, "invariants", false);
        if (invariantMap != null) {
            if (invariantMap.containsKey("maxErrorRate")) {
                builder.maxErrorRate(getDouble(invariantMap, "maxErrorRate", true));
            }
            if (invariantMap.containsKey("maxP95LatencyMs")) {
                builder.maxP95LatencyMs(getDouble(invariantMap, "maxP95LatencyMs", true));
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
        for (Map<String, Object> stepMap : steps) {
            String operation = getString(stepMap, "operation", true);
            long latencyMs = getLong(stepMap, "latencyMs", false, 0L);
            double successRate = getDouble(stepMap, "successRate", false, 1.0);
            if (Double.isNaN(successRate) || successRate < 0.0 || successRate > 1.0) {
                throw new IllegalArgumentException("users.steps.successRate must be in [0..1]");
            }

            userSteps.add(session -> {
                if (latencyMs > 0) {
                    Thread.sleep(latencyMs);
                }

                String randomKey = "dsl.random";
                Random random = session.getAttribute(randomKey, Random.class);
                if (random == null) {
                    random = new Random(session.getSeed());
                    session.putAttribute(randomKey, random);
                }

                return random.nextDouble() <= successRate
                        ? StepResult.success(operation)
                        : StepResult.failure(operation);
            });
        }

        return new StepSequenceUser(userSteps);
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

    private static PhaseType parsePhaseType(String raw) {
        try {
            return PhaseType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown phase type: " + raw, e);
        }
    }
}
