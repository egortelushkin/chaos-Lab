package com.chaosLab;

import java.util.Objects;

public final class StepResult {

    private final boolean success;
    private final String operation;
    private final String orderId;

    private StepResult(boolean success, String operation, String orderId) {
        this.success = success;
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
        this.orderId = orderId;
    }

    public static StepResult success(String operation) {
        return new StepResult(true, operation, null);
    }

    public static StepResult successWithOrderId(String operation, String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        return new StepResult(true, operation, orderId);
    }

    public static StepResult failure(String operation) {
        return new StepResult(false, operation, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOperation() {
        return operation;
    }

    public String getOrderId() {
        return orderId;
    }
}
