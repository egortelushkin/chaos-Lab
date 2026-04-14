package com.chaosLab;

public final class ErrorRateInvariant implements Invariant {

    private final double maxErrorRate;

    public ErrorRateInvariant(double maxErrorRate) {
        if (Double.isNaN(maxErrorRate) || maxErrorRate < 0.0 || maxErrorRate > 1.0) {
            throw new IllegalArgumentException("maxErrorRate must be between 0.0 and 1.0");
        }
        this.maxErrorRate = maxErrorRate;
    }

    @Override
    public InvariantResult evaluate(ExperimentMetrics metrics) {
        double actual = metrics.getErrorRate();
        boolean passed = actual <= maxErrorRate;
        return new InvariantResult(
                "error_rate <= " + maxErrorRate,
                passed,
                "actual=" + actual
        );
    }
}
