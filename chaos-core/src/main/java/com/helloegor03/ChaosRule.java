package com.helloegor03;

import java.util.function.DoubleSupplier;

public class ChaosRule {

    private final DoubleSupplier probabilityProvider;
    private final ChaosEffect effect;

    public ChaosRule(double probability, ChaosEffect effect) {
        this(() -> probability, effect);
    }

    public ChaosRule(DoubleSupplier dynamicProbability, ChaosEffect effect) {
        this.probabilityProvider = dynamicProbability;
        this.effect = effect;
    }

    public boolean shouldApply() {
        return Math.random() < probabilityProvider.getAsDouble();
    }

    public void apply() throws Exception {
        effect.apply();
    }

    public ChaosEffect getEffect() {
        return effect;
    }
}