package com.chaosLab;

import java.util.Objects;

public final class ExperimentRegression {

    private final String baselineExperimentName;
    private final String currentExperimentName;
    private final double errorRateDelta;
    private final double p95LatencyDeltaMs;
    private final double resilienceScoreDelta;

    public ExperimentRegression(
            String baselineExperimentName,
            String currentExperimentName,
            double errorRateDelta,
            double p95LatencyDeltaMs,
            double resilienceScoreDelta
    ) {
        this.baselineExperimentName = Objects.requireNonNull(baselineExperimentName, "baselineExperimentName must not be null");
        this.currentExperimentName = Objects.requireNonNull(currentExperimentName, "currentExperimentName must not be null");
        this.errorRateDelta = errorRateDelta;
        this.p95LatencyDeltaMs = p95LatencyDeltaMs;
        this.resilienceScoreDelta = resilienceScoreDelta;
    }

    public String getBaselineExperimentName() {
        return baselineExperimentName;
    }

    public String getCurrentExperimentName() {
        return currentExperimentName;
    }

    public double getErrorRateDelta() {
        return errorRateDelta;
    }

    public double getP95LatencyDeltaMs() {
        return p95LatencyDeltaMs;
    }

    public double getResilienceScoreDelta() {
        return resilienceScoreDelta;
    }
}
