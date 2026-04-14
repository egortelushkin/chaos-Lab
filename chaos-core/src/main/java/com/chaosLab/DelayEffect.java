package com.chaosLab;

import java.util.concurrent.ThreadLocalRandom;

public class DelayEffect implements ChaosEffect {

    private final int maxDelayMs;

    public DelayEffect(int maxDelayMs) {
        if (maxDelayMs < 0) {
            throw new IllegalArgumentException("maxDelayMs must be >= 0");
        }
        this.maxDelayMs = maxDelayMs;
    }

    @Override
    public void apply() {
        if (maxDelayMs == 0) {
            return;
        }

        int delay = ThreadLocalRandom.current().nextInt(maxDelayMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Delay effect interrupted", interruptedException);
        }
    }

}
