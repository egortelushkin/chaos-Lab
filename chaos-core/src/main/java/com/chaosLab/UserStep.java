package com.chaosLab;

@FunctionalInterface
public interface UserStep {
    StepResult execute(UserSession session) throws Exception;
}
