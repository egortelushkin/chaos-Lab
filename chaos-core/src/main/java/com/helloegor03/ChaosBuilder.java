package com.helloegor03;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

public class ChaosBuilder {
    private final List<ChaosRule> rules = new ArrayList<>();

    public EffectBuilder delay(int maxDelayMs) {
        return new EffectBuilder(this, EffectType.DELAY, maxDelayMs);
    }

    public EffectBuilder exception() {
        return new EffectBuilder(this, EffectType.EXCEPTION, 0);
    }

    void addRule(double probability, ChaosEffect effect) {
        rules.add(new ChaosRule(probability, effect));
    }

    void addRule(DoubleSupplier dynamicProbability, ChaosEffect effect) {
        rules.add(new ChaosRule(dynamicProbability, effect));
    }

    public ChaosEngine build() {
        ChaosEngine engine = new ChaosEngine();
        rules.forEach(engine::addRule);
        return engine;
    }

    public ChaosScenario scenario(String name) {
        return new ChaosScenario(name, build());
    }
}

