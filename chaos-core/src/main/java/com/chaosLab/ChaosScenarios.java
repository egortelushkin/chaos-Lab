package com.chaosLab;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ChaosScenarios {
    private static final Map<String, ChaosScenario> SCENARIOS = new ConcurrentHashMap<>();

    public static void register(ChaosScenario scenario) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        String scenarioName = normalizeKey(Objects.requireNonNull(scenario.getName(), "scenario name must not be null"));
        if (scenarioName == null) {
            throw new IllegalArgumentException("scenario name must not be blank");
        }
        SCENARIOS.put(scenarioName, scenario);
    }

    public static void registerAlias(String alias, ChaosScenario scenario) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        String aliasKey = normalizeKey(alias);
        if (aliasKey == null) {
            throw new IllegalArgumentException("alias must not be blank");
        }
        SCENARIOS.put(aliasKey, scenario);
    }

    public static ChaosScenario get(String name) {
        String key = normalizeKey(name);
        if (key == null) {
            return null;
        }
        ChaosScenario scenario = SCENARIOS.get(key);
        if (scenario != null) {
            return scenario;
        }
        return SCENARIOS.get(legacyAlias(key));
    }

    public static boolean enable(String name) {
        ChaosScenario scenario = get(name);
        if (scenario == null) {
            return false;
        }
        scenario.enable();
        return true;
    }

    public static boolean disable(String name) {
        ChaosScenario scenario = get(name);
        if (scenario == null) {
            return false;
        }
        scenario.disable();
        return true;
    }

    public static int disableAll() {
        int changed = 0;
        for (ChaosScenario scenario : uniqueScenarios()) {
            scenario.disable();
            changed++;
        }
        return changed;
    }

    public static int enableOnly(Collection<String> names) {
        Objects.requireNonNull(names, "names must not be null");
        var normalized = names.stream()
                .map(ChaosScenarios::normalizeKey)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        var allowed = new HashSet<String>();
        for (String key : normalized) {
            allowed.add(key);
            allowed.add(legacyAlias(key));
        }

        int changed = 0;
        for (ChaosScenario scenario : uniqueScenarios()) {
            String key = normalizeKey(scenario.getName());
            if (key != null && allowed.contains(key)) {
                scenario.enable();
            } else {
                scenario.disable();
            }
            changed++;
        }
        return changed;
    }

    public static void unregister(String name) {
        String key = normalizeKey(name);
        if (key == null) {
            return;
        }
        SCENARIOS.remove(key);
    }

    public static void clear() {
        SCENARIOS.clear();
    }

    public static Map<String, ChaosScenario> all() {
        return Map.copyOf(SCENARIOS);
    }

    private static Collection<ChaosScenario> uniqueScenarios() {
        return java.util.Set.copyOf(SCENARIOS.values());
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String legacyAlias(String normalizedKey) {
        return switch (normalizedKey) {
            case "default" -> "defaultchaosscenario";
            case "defaultchaosscenario" -> "default";
            case "stress" -> "stresschaos";
            case "stresschaos" -> "stress";
            default -> normalizedKey;
        };
    }
}
