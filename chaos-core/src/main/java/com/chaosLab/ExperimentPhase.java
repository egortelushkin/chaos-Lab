package com.chaosLab;

import java.time.Duration;
import java.util.Objects;

public final class ExperimentPhase {

    private final String name;
    private final PhaseType type;
    private final Duration duration;

    public ExperimentPhase(String name, PhaseType type, Duration duration) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.duration = Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("phase duration must be > 0");
        }
    }

    public String getName() {
        return name;
    }

    public PhaseType getType() {
        return type;
    }

    public Duration getDuration() {
        return duration;
    }
}
