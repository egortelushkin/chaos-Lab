package com.chaosLab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StepSequenceUser implements SyntheticUser {

    private final List<UserStep> steps;
    private int cursor = 0;

    public StepSequenceUser(List<UserStep> steps) {
        Objects.requireNonNull(steps, "steps must not be null");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        this.steps = List.copyOf(new ArrayList<>(steps));
    }

    @Override
    public StepResult execute(UserSession session) throws Exception {
        UserStep step = steps.get(cursor);
        cursor = (cursor + 1) % steps.size();
        return step.execute(session);
    }

    @Override
    public String nextOperationHint(UserSession session) {
        return steps.get(cursor).operation();
    }
}
