package com.chaosLab;

public interface SyntheticUser {

    default void onStart(UserSession session) {
        // no-op
    }

    StepResult execute(UserSession session) throws Exception;

    default void onFinish(UserSession session) {
        // no-op
    }
}
