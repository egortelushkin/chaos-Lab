package com.chaosLab;

public interface SyntheticUser {

    default void onStart(UserSession session) {
        // no-op
    }

    StepResult execute(UserSession session) throws Exception;

    default String nextOperationHint(UserSession session) {
        return null;
    }

    default void onFinish(UserSession session) {
        // no-op
    }
}
