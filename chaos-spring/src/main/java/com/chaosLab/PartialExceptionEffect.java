package com.chaosLab;

import java.util.concurrent.ThreadLocalRandom;

public class PartialExceptionEffect implements ChaosEffect {

    private final double probability;

    public PartialExceptionEffect(double probability) {
        this.probability = probability;
    }

    @Override
    public void apply() {
        if (ThreadLocalRandom.current().nextDouble() < probability) {
            throw new RuntimeException("Partial exception chaos effect!");
        }
    }
}
