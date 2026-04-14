package com.chaosLab;

public final class P95LatencyInvariant implements Invariant {

    private final double maxP95LatencyMs;

    public P95LatencyInvariant(double maxP95LatencyMs) {
        if (Double.isNaN(maxP95LatencyMs) || maxP95LatencyMs < 0.0) {
            throw new IllegalArgumentException("maxP95LatencyMs must be >= 0");
        }
        this.maxP95LatencyMs = maxP95LatencyMs;
    }

    @Override
    public InvariantResult evaluate(ExperimentMetrics metrics) {
        double actual = metrics.getP95LatencyMs();
        boolean passed = actual <= maxP95LatencyMs;
        return new InvariantResult(
                "p95_latency_ms <= " + maxP95LatencyMs,
                passed,
                "actual=" + actual
        );
    }
}
