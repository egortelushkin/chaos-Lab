package com.chaosLab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ExperimentReport {

    private final String experimentName;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final ExperimentStatus status;
    private final ExperimentMetrics metrics;
    private final double resilienceScore;
    private final List<PhaseReport> phaseReports;
    private final List<InvariantResult> invariantResults;
    private final List<Throwable> executionErrors;

    public ExperimentReport(
            String experimentName,
            Instant startedAt,
            Instant finishedAt,
            ExperimentStatus status,
            ExperimentMetrics metrics,
            double resilienceScore,
            List<PhaseReport> phaseReports,
            List<InvariantResult> invariantResults,
            List<Throwable> executionErrors
    ) {
        this.experimentName = Objects.requireNonNull(experimentName, "experimentName must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.resilienceScore = resilienceScore;
        this.phaseReports = Collections.unmodifiableList(new ArrayList<>(phaseReports));
        this.invariantResults = Collections.unmodifiableList(new ArrayList<>(invariantResults));
        this.executionErrors = Collections.unmodifiableList(new ArrayList<>(executionErrors));
    }

    public String getExperimentName() {
        return experimentName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public ExperimentStatus getStatus() {
        return status;
    }

    public ExperimentMetrics getMetrics() {
        return metrics;
    }

    public double getResilienceScore() {
        return resilienceScore;
    }

    public List<PhaseReport> getPhaseReports() {
        return phaseReports;
    }

    public List<InvariantResult> getInvariantResults() {
        return invariantResults;
    }

    public List<Throwable> getExecutionErrors() {
        return executionErrors;
    }

    public boolean isPassed() {
        return status == ExperimentStatus.PASS;
    }
}
