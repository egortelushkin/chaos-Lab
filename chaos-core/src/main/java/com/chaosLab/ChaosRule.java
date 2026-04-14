package com.chaosLab;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.random.RandomGenerator;

public class ChaosRule {

    private final DoubleSupplier probabilityProvider;
    private final ChaosEffect effect;

    public ChaosRule(double probability, ChaosEffect effect) {
        this(() -> probability, effect);
    }

    public ChaosRule(DoubleSupplier dynamicProbability, ChaosEffect effect) {
        this.probabilityProvider = Objects.requireNonNull(dynamicProbability, "dynamicProbability must not be null");
        this.effect = Objects.requireNonNull(effect, "effect must not be null");
    }

    public boolean shouldApply() {
        return shouldApply(ThreadLocalRandom.current());
    }

    boolean shouldApply(RandomGenerator random) {
        double probability = probabilityProvider.getAsDouble();
        if (Double.isNaN(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalStateException("Probability must be between 0.0 and 1.0, got " + probability);
        }
        return random.nextDouble() < probability;
    }

    public void apply() throws Exception {
        effect.before();
        try {
            effect.apply();
        } finally {
            effect.after();
        }
    }

    public ChaosEffect getEffect() {
        return effect;
    }
}
