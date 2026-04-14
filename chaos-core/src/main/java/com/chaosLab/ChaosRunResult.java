package com.chaosLab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChaosRunResult {

    private final int totalRules;
    private final int appliedRules;
    private final int skippedRules;
    private final long durationMs;
    private final List<Throwable> failures;

    public ChaosRunResult(
            int totalRules,
            int appliedRules,
            int skippedRules,
            long durationMs,
            List<Throwable> failures
    ) {
        this.totalRules = totalRules;
        this.appliedRules = appliedRules;
        this.skippedRules = skippedRules;
        this.durationMs = durationMs;
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
    }

    public int getTotalRules() {
        return totalRules;
    }

    public int getAppliedRules() {
        return appliedRules;
    }

    public int getSkippedRules() {
        return skippedRules;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public List<Throwable> getFailures() {
        return failures;
    }

    public boolean isSuccess() {
        return failures.isEmpty();
    }
}
