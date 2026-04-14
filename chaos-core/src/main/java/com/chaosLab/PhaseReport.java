package com.chaosLab;

import java.util.Objects;

public final class PhaseReport {

    private final String phaseName;
    private final PhaseType phaseType;
    private final ExperimentMetrics metrics;

    public PhaseReport(String phaseName, PhaseType phaseType, ExperimentMetrics metrics) {
        this.phaseName = Objects.requireNonNull(phaseName, "phaseName must not be null");
        this.phaseType = Objects.requireNonNull(phaseType, "phaseType must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public String getPhaseName() {
        return phaseName;
    }

    public PhaseType getPhaseType() {
        return phaseType;
    }

    public ExperimentMetrics getMetrics() {
        return metrics;
    }
}
