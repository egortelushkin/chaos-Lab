package com.helloegor03;

import java.util.ArrayList;
import java.util.List;

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

    public ChaosEngine build() {
        ChaosEngine engine = new ChaosEngine();
        rules.forEach(engine::addRule);
        return engine;
    }
}
