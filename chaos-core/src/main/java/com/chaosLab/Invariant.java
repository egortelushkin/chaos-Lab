package com.chaosLab;

@FunctionalInterface
public interface Invariant {
    InvariantResult evaluate(ExperimentMetrics metrics);
}
