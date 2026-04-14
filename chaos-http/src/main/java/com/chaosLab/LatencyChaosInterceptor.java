package com.chaosLab;

import java.util.concurrent.ThreadLocalRandom;

public class LatencyChaosInterceptor implements ChaosHttpInterceptor {

    private final long maxDelayMs;

    public LatencyChaosInterceptor(long maxDelayMs) {
        if (maxDelayMs < 0) {
            throw new IllegalArgumentException("maxDelayMs must be >= 0");
        }
        this.maxDelayMs = maxDelayMs;
    }

    @Override
    public void beforeRequest() {
        if (maxDelayMs == 0) {
            return;
        }
        long delay = ThreadLocalRandom.current().nextLong(maxDelayMs);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Latency chaos interceptor interrupted", e);
        }
    }
}
