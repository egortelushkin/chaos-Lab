package com.helloegor03;

import java.util.concurrent.ThreadLocalRandom;

public class LatencyChaosInterceptor implements ChaosHttpInterceptor {

    private final long maxDelayMs;

    public LatencyChaosInterceptor(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    @Override
    public void beforeRequest() {
        long delay = ThreadLocalRandom.current().nextLong(maxDelayMs);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}