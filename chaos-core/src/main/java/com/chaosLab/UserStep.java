package com.chaosLab;

import java.util.Objects;

@FunctionalInterface
public interface UserStep {
    StepResult execute(UserSession session) throws Exception;

    default String operation() {
        return null;
    }

    static UserStep named(String operation, UserStep delegate) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(delegate, "delegate must not be null");
        return new UserStep() {
            @Override
            public StepResult execute(UserSession session) throws Exception {
                return delegate.execute(session);
            }

            @Override
            public String operation() {
                return operation;
            }
        };
    }
}
