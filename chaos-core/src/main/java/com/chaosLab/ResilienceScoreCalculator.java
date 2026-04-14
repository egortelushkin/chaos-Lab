package com.chaosLab;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

final class ResilienceScoreCalculator {

    private ResilienceScoreCalculator() {
    }

    static double calculate(
            ExperimentMetrics metrics,
            List<InvariantResult> invariantResults,
            List<Throwable> executionErrors
    ) {
        double score = 100.0;

        score -= metrics.getErrorRate() * 70.0;
        score -= Math.min(20.0, metrics.getP95LatencyMs() / 50.0);

        long failedInvariants = invariantResults.stream()
                .filter(result -> !result.isPassed())
                .count();
        score -= failedInvariants * 15.0;

        score -= Math.min(20.0, executionErrors.size() * 5.0);

        if (score < 0.0) {
            score = 0.0;
        }
        if (score > 100.0) {
            score = 100.0;
        }

        return BigDecimal.valueOf(score)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
