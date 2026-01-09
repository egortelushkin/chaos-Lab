package com.helloegor03;

import java.util.HashMap;
import java.util.Map;

public class ChaosScenarios {
    private static final Map<String, ChaosScenario> SCENARIOS = new HashMap<>();

    public static void register(ChaosScenario scenario) {
        SCENARIOS.put(scenario.getName(), scenario);
    }

    public static ChaosScenario get(String name) {
        return SCENARIOS.get(name);
    }
}