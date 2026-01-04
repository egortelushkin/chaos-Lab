package com.helloegor03;

public class ChaosRule {

    private final double probability;
    private final ChaosEffect effect;

    public ChaosRule(double probability, ChaosEffect effect) {
        this.probability = probability;
        this.effect = effect;
    }

    public boolean shouldApply() {
        return Math.random() < probability;
    }

    public void apply() throws Exception {
        effect.apply();
    }
}