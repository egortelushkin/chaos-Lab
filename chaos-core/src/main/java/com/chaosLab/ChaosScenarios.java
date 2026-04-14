package com.chaosLab;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ChaosScenarios {
    private static final Map<String, ChaosScenario> SCENARIOS = new ConcurrentHashMap<>();

    public static void register(ChaosScenario scenario) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        String scenarioName = Objects.requireNonNull(scenario.getName(), "scenario name must not be null").trim();
        if (scenarioName.isEmpty()) {
            throw new IllegalArgumentException("scenario name must not be blank");
        }
        SCENARIOS.put(scenarioName, scenario);
    }

    public static ChaosScenario get(String name) {
        return SCENARIOS.get(name);
    }

    public static void unregister(String name) {
        SCENARIOS.remove(name);
    }

    public static void clear() {
        SCENARIOS.clear();
    }

    public static Map<String, ChaosScenario> all() {
        return Map.copyOf(SCENARIOS);
    }
}
