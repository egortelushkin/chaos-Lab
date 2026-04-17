package com.chaosLab;

import java.util.Objects;
import java.util.List;

public class ChaosScenario {
    private final String name;
    private final ChaosEngine engine;
    private boolean enabled = true;

    public ChaosScenario(String name, ChaosEngine engine) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void unleash() {
        if (enabled) engine.unleash();
    }

    public ChaosRunResult run() {
        if (!enabled) {
            int totalRules = engine.getRuleCount();
            return new ChaosRunResult(totalRules, 0, totalRules, 0, List.of());
        }
        return engine.run();
    }

    public String getName() {
        return name;
    }

    public ChaosEngine getEngine() {
        return engine;
    }
}
