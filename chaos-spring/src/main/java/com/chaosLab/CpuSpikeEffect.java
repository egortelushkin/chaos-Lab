package com.chaosLab;

public class CpuSpikeEffect implements ChaosEffect {

    private final long durationMs;

    public CpuSpikeEffect(long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public void apply() {
        long end = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < end) {
            Math.pow(Math.random(), Math.random()); // load for CPU
        }
    }
}