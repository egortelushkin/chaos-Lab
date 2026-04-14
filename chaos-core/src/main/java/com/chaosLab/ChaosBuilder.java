package com.chaosLab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

public class ChaosBuilder {
    private final List<ChaosRule> rules = new ArrayList<>();
    private ChaosExecutionMode executionMode = ChaosExecutionMode.PARALLEL;
    private Long randomSeed;

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

    public ChaosBuilder executionMode(ChaosExecutionMode executionMode) {
        this.executionMode = Objects.requireNonNull(executionMode, "executionMode must not be null");
        return this;
    }

    public ChaosBuilder sequential() {
        return executionMode(ChaosExecutionMode.SEQUENTIAL);
    }

    public ChaosBuilder parallel() {
        return executionMode(ChaosExecutionMode.PARALLEL);
    }

    public ChaosBuilder withSeed(long seed) {
        this.randomSeed = seed;
        return this;
    }

    public ChaosEngine build() {
        ChaosEngine engine = new ChaosEngine(executionMode, randomSeed);
        rules.forEach(engine::addRule);
        return engine;
    }

    public ChaosScenario scenario(String name) {
        return new ChaosScenario(name, build());
    }
}

