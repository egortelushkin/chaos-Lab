package com.helloegor03;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class DelayEffect implements ChaosEffect {

    private final int maxDelayMs;

    public DelayEffect(int maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    @Override
    public void apply() {
        int delay = ThreadLocalRandom.current().nextInt(maxDelayMs);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {}
        });
    }
}