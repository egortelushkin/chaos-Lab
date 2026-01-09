package com.helloegor03;

public class ChaosScenario {
    private final String name;
    private final ChaosEngine engine;
    private boolean enabled = true;

    public ChaosScenario(String name, ChaosEngine engine) {
        this.name = name;
        this.engine = engine;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public void unleash() {
        if (enabled) engine.unleash();
    }

    public String getName() {
        return name;
    }
}
