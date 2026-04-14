package com.chaosLab;

public record RegressionThresholds(
        double maxErrorRateIncrease,
        double maxP95LatencyIncreaseMs,
        double maxResilienceScoreDrop
) {

    public RegressionThresholds {
        if (maxErrorRateIncrease < 0.0 || Double.isNaN(maxErrorRateIncrease)) {
            throw new IllegalArgumentException("maxErrorRateIncrease must be >= 0");
        }
        if (maxP95LatencyIncreaseMs < 0.0 || Double.isNaN(maxP95LatencyIncreaseMs)) {
            throw new IllegalArgumentException("maxP95LatencyIncreaseMs must be >= 0");
        }
        if (maxResilienceScoreDrop < 0.0 || Double.isNaN(maxResilienceScoreDrop)) {
            throw new IllegalArgumentException("maxResilienceScoreDrop must be >= 0");
        }
    }
}
