package com.chaosLab;

import java.util.Objects;

public final class StepResult {

    private final boolean success;
    private final String operation;

    private StepResult(boolean success, String operation) {
        this.success = success;
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
    }

    public static StepResult success(String operation) {
        return new StepResult(true, operation);
    }

    public static StepResult failure(String operation) {
        return new StepResult(false, operation);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOperation() {
        return operation;
    }
}
