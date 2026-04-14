package com.chaosLab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public class ChaosEngine {

    private final List<ChaosRule> rules = new ArrayList<>();
    private final ChaosExecutionMode executionMode;
    private final Long randomSeed;

    public ChaosEngine() {
        this(ChaosExecutionMode.PARALLEL, null);
    }

    ChaosEngine(ChaosExecutionMode executionMode, Long randomSeed) {
        this.executionMode = executionMode;
        this.randomSeed = randomSeed;
    }

    public ChaosEngine addRule(ChaosRule rule) {
        rules.add(rule);
        return this;
    }

    public void unleash() {
        ChaosRunResult result = run();
        if (!result.getFailures().isEmpty()) {
            Throwable first = result.getFailures().get(0);
            RuntimeException exception = first instanceof RuntimeException
                    ? (RuntimeException) first
                    : new RuntimeException(first);
            for (int i = 1; i < result.getFailures().size(); i++) {
                exception.addSuppressed(result.getFailures().get(i));
            }
            throw exception;
        }
    }

    public ChaosRunResult run() {
        long startedAt = System.nanoTime();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        List<ChaosRule> rulesSnapshot = new ArrayList<>(rules);
        List<ChaosRule> rulesToApply = chooseRulesToApply(rulesSnapshot);

        if (executionMode == ChaosExecutionMode.SEQUENTIAL) {
            applySequentially(rulesToApply, failures);
        } else {
            applyInParallel(rulesToApply, failures);
        }

        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return new ChaosRunResult(
                rulesSnapshot.size(),
                rulesToApply.size(),
                rulesSnapshot.size() - rulesToApply.size(),
                durationMs,
                failures
        );
    }

    public ChaosExecutionMode getExecutionMode() {
        return executionMode;
    }

    public Long getRandomSeed() {
        return randomSeed;
    }

    public int getRuleCount() {
        return rules.size();
    }

    private List<ChaosRule> chooseRulesToApply(List<ChaosRule> rulesSnapshot) {
        RandomGenerator random = randomSeed == null
                ? ThreadLocalRandom.current()
                : new Random(randomSeed);

        List<ChaosRule> rulesToApply = new ArrayList<>();
        for (ChaosRule rule : rulesSnapshot) {
            if (rule.shouldApply(random)) {
                rulesToApply.add(rule);
            }
        }
        return rulesToApply;
    }

    private void applySequentially(List<ChaosRule> rulesToApply, List<Throwable> failures) {
        for (ChaosRule rule : rulesToApply) {
            applyRule(rule, failures);
        }
    }

    private void applyInParallel(List<ChaosRule> rulesToApply, List<Throwable> failures) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ChaosRule rule : rulesToApply) {
            futures.add(CompletableFuture.runAsync(() -> applyRule(rule, failures)));
        }
        futures.forEach(CompletableFuture::join);
    }

    private void applyRule(ChaosRule rule, List<Throwable> failures) {
        try {
            rule.apply();
        } catch (Throwable throwable) {
            failures.add(throwable);
        }
    }
}
