package com.chaosLab;

import java.util.Objects;

public final class ExperimentRegressionAnalyzer {

    private ExperimentRegressionAnalyzer() {
    }

    public static ExperimentRegression compare(ExperimentReport baseline, ExperimentReport current) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(current, "current must not be null");

        return new ExperimentRegression(
                baseline.getExperimentName(),
                current.getExperimentName(),
                current.getMetrics().getErrorRate() - baseline.getMetrics().getErrorRate(),
                current.getMetrics().getP95LatencyMs() - baseline.getMetrics().getP95LatencyMs(),
                current.getResilienceScore() - baseline.getResilienceScore()
        );
    }
}
