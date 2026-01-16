package com.chaosLab;

import java.util.function.DoubleSupplier;

public class EffectBuilder {
    private final ChaosBuilder parent;
    private final ChaosEffect effect;

    EffectBuilder(ChaosBuilder parent, EffectType type, int value) {
        this.parent = parent;
        this.effect = switch (type) {
            case DELAY -> new DelayEffect(value);
            case EXCEPTION -> new ExceptionEffect();
        };
    }

    public ChaosBuilder probability(double probability) {
        parent.addRule(probability, effect);
        return parent;
    }

    public ChaosBuilder dynamicProbability(DoubleSupplier dynamicProbability) {
        parent.addRule(dynamicProbability, effect);
        return parent;
    }
}
