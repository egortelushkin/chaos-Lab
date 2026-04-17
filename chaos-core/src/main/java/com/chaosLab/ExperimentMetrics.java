package com.chaosLab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExperimentMetrics {

    private final long totalOperations;
    private final long successfulOperations;
    private final long failedOperations;
    private final double errorRate;
    private final double p95LatencyMs;
    private final double avgLatencyMs;
    private final long maxLatencyMs;
    private final long uniqueOrderIds;
    private final long duplicateOrderIds;
    private final List<Long> latenciesMs;

    public ExperimentMetrics(
            long totalOperations,
            long successfulOperations,
            long failedOperations,
            double errorRate,
            double p95LatencyMs,
            double avgLatencyMs,
            long maxLatencyMs,
            long uniqueOrderIds,
            long duplicateOrderIds,
            List<Long> latenciesMs
    ) {
        this.totalOperations = totalOperations;
        this.successfulOperations = successfulOperations;
        this.failedOperations = failedOperations;
        this.errorRate = errorRate;
        this.p95LatencyMs = p95LatencyMs;
        this.avgLatencyMs = avgLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.uniqueOrderIds = uniqueOrderIds;
        this.duplicateOrderIds = duplicateOrderIds;
        this.latenciesMs = Collections.unmodifiableList(new ArrayList<>(latenciesMs));
    }

    public long getTotalOperations() {
        return totalOperations;
    }

    public long getSuccessfulOperations() {
        return successfulOperations;
    }

    public long getFailedOperations() {
        return failedOperations;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public double getP95LatencyMs() {
        return p95LatencyMs;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public long getUniqueOrderIds() {
        return uniqueOrderIds;
    }

    public long getDuplicateOrderIds() {
        return duplicateOrderIds;
    }

    public List<Long> getLatenciesMs() {
        return latenciesMs;
    }
}
