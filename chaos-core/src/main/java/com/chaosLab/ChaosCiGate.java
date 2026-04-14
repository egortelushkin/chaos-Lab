package com.chaosLab;

import java.util.Objects;
import java.util.stream.Collectors;

public final class ChaosCiGate {

    private ChaosCiGate() {
    }

    public static void assertPassed(ExperimentReport report) {
        Objects.requireNonNull(report, "report must not be null");
        if (report.isPassed()) {
            return;
        }

        String failedInvariants = report.getInvariantResults().stream()
                .filter(result -> !result.isPassed())
                .map(result -> result.getName() + " (" + result.getDetails() + ")")
                .collect(Collectors.joining(", "));

        if (failedInvariants.isBlank()) {
            failedInvariants = "none";
        }

        throw new IllegalStateException(
                "Chaos experiment '" + report.getExperimentName() + "' failed. " +
                        "resilienceScore=" + report.getResilienceScore() + ", " +
                        "errorRate=" + report.getMetrics().getErrorRate() + ", " +
                        "p95LatencyMs=" + report.getMetrics().getP95LatencyMs() + ", " +
                        "failedInvariants=" + failedInvariants + ", " +
                        "executionErrors=" + report.getExecutionErrors().size()
        );
    }

    public static int exitCode(ExperimentReport report) {
        Objects.requireNonNull(report, "report must not be null");
        return report.isPassed() ? 0 : 1;
    }

    public static void assertNoRegression(
            ExperimentReport baseline,
            ExperimentReport current,
            RegressionThresholds thresholds
    ) {
        Objects.requireNonNull(baseline, "baseline must not be null");
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(thresholds, "thresholds must not be null");

        ExperimentRegression regression = ExperimentRegressionAnalyzer.compare(baseline, current);

        boolean errorRateRegressed = regression.getErrorRateDelta() > thresholds.maxErrorRateIncrease();
        boolean p95Regressed = regression.getP95LatencyDeltaMs() > thresholds.maxP95LatencyIncreaseMs();
        boolean scoreRegressed = -regression.getResilienceScoreDelta() > thresholds.maxResilienceScoreDrop();

        if (!errorRateRegressed && !p95Regressed && !scoreRegressed) {
            return;
        }

        throw new IllegalStateException(
                "Chaos regression detected between '" + baseline.getExperimentName() + "' and '" + current.getExperimentName() + "'. " +
                        "errorRateDelta=" + regression.getErrorRateDelta() + " (max " + thresholds.maxErrorRateIncrease() + "), " +
                        "p95DeltaMs=" + regression.getP95LatencyDeltaMs() + " (max " + thresholds.maxP95LatencyIncreaseMs() + "), " +
                        "resilienceScoreDelta=" + regression.getResilienceScoreDelta() + " (min -" + thresholds.maxResilienceScoreDrop() + ")"
        );
    }
}
