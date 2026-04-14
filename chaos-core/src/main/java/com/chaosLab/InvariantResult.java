package com.chaosLab;

import java.util.Objects;

public final class InvariantResult {

    private final String name;
    private final boolean passed;
    private final String details;

    public InvariantResult(String name, boolean passed, String details) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.passed = passed;
        this.details = Objects.requireNonNull(details, "details must not be null");
    }

    public String getName() {
        return name;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getDetails() {
        return details;
    }
}
