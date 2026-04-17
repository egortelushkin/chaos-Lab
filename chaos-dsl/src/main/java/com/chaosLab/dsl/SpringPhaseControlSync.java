package com.chaosLab.dsl;

import com.chaosLab.ChaosExperiment;
import com.chaosLab.ExperimentPhase;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class SpringPhaseControlSync {

    private SpringPhaseControlSync() {
    }

    static SpringControlConfig parseFromYaml(String yamlText) {
        Objects.requireNonNull(yamlText, "yamlText must not be null");
        Object loaded = new Yaml().load(new StringReader(yamlText));
        if (!(loaded instanceof Map<?, ?> rootRaw)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rootRaw;
        Map<String, Object> runtime = getMap(root, "runtime", false);
        if (runtime == null) {
            return null;
        }
        Map<String, Object> springControl = getMap(runtime, "springControl", false);
        if (springControl == null) {
            return null;
        }

        boolean enabled = getBoolean(springControl, "enabled", false, true);
        if (!enabled) {
            return null;
        }

        String baseUrl = normalizeBaseUrl(getString(springControl, "baseUrl", true));
        long timeoutMs = getLong(springControl, "timeoutMs", false, 3000L);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("runtime.springControl.timeoutMs must be > 0");
        }

        boolean failOnError = getBoolean(springControl, "failOnError", false, true);
        boolean disableAllAfterRun = getBoolean(springControl, "disableAllAfterRun", false, true);
        List<String> warmupScenarios = springControl.containsKey("warmupScenarios")
                ? getListOfStrings(springControl, "warmupScenarios", false)
                : List.of("default");
        List<String> faultScenarios = springControl.containsKey("faultScenarios")
                ? getListOfStrings(springControl, "faultScenarios", false)
                : List.of("stress");
        List<String> recoveryScenarios = springControl.containsKey("recoveryScenarios")
                ? getListOfStrings(springControl, "recoveryScenarios", false)
                : List.of("default");

        return new SpringControlConfig(
                baseUrl,
                timeoutMs,
                failOnError,
                disableAllAfterRun,
                List.copyOf(warmupScenarios),
                List.copyOf(faultScenarios),
                List.copyOf(recoveryScenarios)
        );
    }

    static PhaseSyncHandle start(SpringControlConfig config, ChaosExperiment experiment) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(experiment, "experiment must not be null");
        Worker worker = new Worker(config, experiment.getPhases());
        Thread thread = new Thread(worker, "spring-phase-control-sync");
        thread.setDaemon(true);
        thread.start();
        return new PhaseSyncHandle(worker, thread);
    }

    static PhaseSyncResult emptyResult() {
        return new PhaseSyncResult(0, 0, List.of(), List.of());
    }

    static final class PhaseSyncHandle {
        private final Worker worker;
        private final Thread thread;

        private PhaseSyncHandle(Worker worker, Thread thread) {
            this.worker = worker;
            this.thread = thread;
        }

        PhaseSyncResult finish() {
            try {
                thread.join(2000L);
                if (thread.isAlive()) {
                    thread.interrupt();
                    thread.join(500L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker.applyPostRunState();
            return worker.result();
        }
    }

    private static final class Worker implements Runnable {
        private final SpringControlConfig config;
        private final List<ExperimentPhase> phases;
        private final HttpClient client;
        private final AtomicInteger requestedSwitches = new AtomicInteger();
        private final AtomicInteger successfulSwitches = new AtomicInteger();
        private final CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> timeline = new CopyOnWriteArrayList<>();

        private Worker(SpringControlConfig config, List<ExperimentPhase> phases) {
            this.config = config;
            this.phases = List.copyOf(phases);
            this.client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.timeoutMs()))
                    .build();
        }

        @Override
        public void run() {
            for (int i = 0; i < phases.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                ExperimentPhase phase = phases.get(i);
                applyPhaseState(phase, "phase-start");
                if (i < phases.size() - 1) {
                    if (!sleepMillis(phase.getDuration().toMillis())) {
                        break;
                    }
                }
            }
        }

        private void applyPostRunState() {
            if (!config.disableAllAfterRun()) {
                return;
            }
            postEnableOnly(List.of(), "post-run");
        }

        private void applyPhaseState(ExperimentPhase phase, String reason) {
            List<String> scenarios = switch (phase.getType()) {
                case WARMUP -> config.warmupScenarios();
                case FAULT -> config.faultScenarios();
                case RECOVERY -> config.recoveryScenarios();
            };
            postEnableOnly(scenarios, reason + ":" + phase.getName());
        }

        private void postEnableOnly(List<String> scenarios, String reason) {
            requestedSwitches.incrementAndGet();
            timeline.add(Instant.now() + " -> " + reason + " -> " + scenarios);

            String payload = buildEnableOnlyPayload(scenarios);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.baseUrl() + "/chaos/control/scenarios/enable-only"))
                        .timeout(Duration.ofMillis(config.timeoutMs()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    successfulSwitches.incrementAndGet();
                } else {
                    errors.add("HTTP " + response.statusCode() + " for " + reason + ", scenarios=" + scenarios);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add("InterruptedException: interrupted for " + reason + ", scenarios=" + scenarios);
            } catch (Exception e) {
                errors.add(e.getClass().getSimpleName() + ": " + e.getMessage() + " for " + reason + ", scenarios=" + scenarios);
            }
        }

        private PhaseSyncResult result() {
            return new PhaseSyncResult(
                    requestedSwitches.get(),
                    successfulSwitches.get(),
                    List.copyOf(errors),
                    List.copyOf(timeline)
            );
        }

        private boolean sleepMillis(long millis) {
            long remaining = millis;
            while (remaining > 0 && !Thread.currentThread().isInterrupted()) {
                long chunk = Math.min(remaining, 200L);
                try {
                    Thread.sleep(chunk);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                remaining -= chunk;
            }
            return !Thread.currentThread().isInterrupted();
        }
    }

    private static String buildEnableOnlyPayload(List<String> scenarios) {
        StringBuilder json = new StringBuilder();
        json.append("{\"names\":[");
        for (int i = 0; i < scenarios.size(); i++) {
            String scenario = scenarios.get(i);
            json.append("\"").append(escape(scenario)).append("\"");
            if (i < scenarios.size() - 1) {
                json.append(",");
            }
        }
        json.append("]}");
        return json.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
            String normalized = String.valueOf(element).trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
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
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException("Expected boolean at key: " + key + ", got: " + value);
        };
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    record SpringControlConfig(
            String baseUrl,
            long timeoutMs,
            boolean failOnError,
            boolean disableAllAfterRun,
            List<String> warmupScenarios,
            List<String> faultScenarios,
            List<String> recoveryScenarios
    ) {
        SpringControlConfig {
            Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            warmupScenarios = warmupScenarios == null ? List.of() : List.copyOf(warmupScenarios);
            faultScenarios = faultScenarios == null ? List.of() : List.copyOf(faultScenarios);
            recoveryScenarios = recoveryScenarios == null ? List.of() : List.copyOf(recoveryScenarios);
        }
    }

    record PhaseSyncResult(
            int requestedSwitches,
            int successfulSwitches,
            List<String> errors,
            List<String> timeline
    ) {
        PhaseSyncResult {
            errors = errors == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(errors));
            timeline = timeline == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(timeline));
        }
    }
}
