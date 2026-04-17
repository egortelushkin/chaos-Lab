package com.chaosLab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ChaosExperimentRunner {

    public ExperimentReport run(ChaosExperiment experiment) {
        Objects.requireNonNull(experiment, "experiment must not be null");

        Instant startedAt = Instant.now();
        long experimentStartedNanos = System.nanoTime();
        long totalDurationNanos = totalDurationNanos(experiment.getPhases());
        long deadlineNanos = experimentStartedNanos + totalDurationNanos;

        MetricsCollector metricsCollector = new MetricsCollector();
        Map<ExperimentPhase, MetricsCollector> phaseMetricsCollectors = initPhaseCollectors(experiment.getPhases());
        List<Throwable> executionErrors = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(experiment.getWorkerThreads());
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int userId = 0; userId < experiment.getVirtualUsers(); userId++) {
                final int resolvedUserId = userId;
                futures.add(pool.submit(buildUserTask(
                        experiment,
                        resolvedUserId,
                        experimentStartedNanos,
                        deadlineNanos,
                        metricsCollector,
                        phaseMetricsCollectors,
                        executionErrors
                )));
            }

            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            executionErrors.add(e);
        } finally {
            pool.shutdownNow();
        }

        ExperimentMetrics metrics = metricsCollector.snapshot();
        List<PhaseReport> phaseReports = buildPhaseReports(phaseMetricsCollectors);
        List<InvariantResult> invariantResults = evaluateInvariants(experiment, metrics);
        ExperimentStatus status = determineStatus(invariantResults, executionErrors);
        double resilienceScore = ResilienceScoreCalculator.calculate(metrics, invariantResults, executionErrors);

        return new ExperimentReport(
                experiment.getName(),
                startedAt,
                Instant.now(),
                status,
                metrics,
                resilienceScore,
                phaseReports,
                invariantResults,
                executionErrors
        );
    }

    private Callable<Void> buildUserTask(
            ChaosExperiment experiment,
            int userId,
            long experimentStartedNanos,
            long deadlineNanos,
            MetricsCollector metricsCollector,
            Map<ExperimentPhase, MetricsCollector> phaseMetricsCollectors,
            List<Throwable> executionErrors
    ) {
        return () -> {
            long baseSeed = experiment.getSeed() == null ? System.nanoTime() : experiment.getSeed();
            UserSession session = new UserSession(userId, baseSeed + userId);
            SyntheticUser syntheticUser = experiment.getUserFactory().get();
            if (syntheticUser == null) {
                throw new IllegalStateException("users(...) supplier returned null");
            }

            syntheticUser.onStart(session);
            try {
                while (System.nanoTime() < deadlineNanos && !Thread.currentThread().isInterrupted()) {
                    long now = System.nanoTime();
                    ExperimentPhase phase = resolvePhase(experiment.getPhases(), now - experimentStartedNanos);
                    executeOneIteration(
                            experiment,
                            syntheticUser,
                            session,
                            phase,
                            metricsCollector,
                            phaseMetricsCollectors.get(phase)
                    );
                    if (experiment.getThinkTimeMs() > 0) {
                        Thread.sleep(experiment.getThinkTimeMs());
                    }
                }
            } finally {
                syntheticUser.onFinish(session);
            }
            return null;
        };
    }

    private void executeOneIteration(
            ChaosExperiment experiment,
            SyntheticUser syntheticUser,
            UserSession session,
            ExperimentPhase phase,
            MetricsCollector totalMetricsCollector,
            MetricsCollector phaseMetricsCollector
    ) {
        long started = System.nanoTime();
        boolean success;
        String orderId = null;
        try {
            if (shouldInjectFault(experiment, syntheticUser, session, phase)) {
                experiment.getFaultEngine().unleash();
            }
            StepResult result = syntheticUser.execute(session);
            success = result != null && result.isSuccess();
            if (result != null) {
                orderId = result.getOrderId();
            }
        } catch (Throwable throwable) {
            success = false;
        }
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        totalMetricsCollector.record(durationMs, success, orderId);
        if (phaseMetricsCollector != null) {
            phaseMetricsCollector.record(durationMs, success, orderId);
        }
    }

    private boolean shouldInjectFault(
            ChaosExperiment experiment,
            SyntheticUser syntheticUser,
            UserSession session,
            ExperimentPhase phase
    ) {
        if (phase.getType() != PhaseType.FAULT || experiment.getFaultEngine() == null) {
            return false;
        }

        Set<String> targetOperations = experiment.getFaultTargetOperations();
        if (targetOperations.isEmpty()) {
            return true;
        }

        String operationHint = syntheticUser.nextOperationHint(session);
        if (operationHint == null || operationHint.isBlank()) {
            return false;
        }
        return targetOperations.contains(operationHint.trim());
    }

    private List<InvariantResult> evaluateInvariants(ChaosExperiment experiment, ExperimentMetrics metrics) {
        List<InvariantResult> results = new ArrayList<>();
        for (Invariant invariant : experiment.getInvariants()) {
            results.add(invariant.evaluate(metrics));
        }
        return results;
    }

    private ExperimentStatus determineStatus(List<InvariantResult> invariantResults, List<Throwable> executionErrors) {
        boolean invariantsPassed = invariantResults.stream().allMatch(InvariantResult::isPassed);
        boolean noExecutionErrors = executionErrors.isEmpty();
        return invariantsPassed && noExecutionErrors ? ExperimentStatus.PASS : ExperimentStatus.FAIL;
    }

    private long totalDurationNanos(List<ExperimentPhase> phases) {
        long durationNanos = 0L;
        for (ExperimentPhase phase : phases) {
            durationNanos += phase.getDuration().toNanos();
        }
        return durationNanos;
    }

    private ExperimentPhase resolvePhase(List<ExperimentPhase> phases, long elapsedNanos) {
        long cursor = 0L;
        for (ExperimentPhase phase : phases) {
            cursor += phase.getDuration().toNanos();
            if (elapsedNanos < cursor) {
                return phase;
            }
        }
        return phases.get(phases.size() - 1);
    }

    private Map<ExperimentPhase, MetricsCollector> initPhaseCollectors(List<ExperimentPhase> phases) {
        Map<ExperimentPhase, MetricsCollector> map = new LinkedHashMap<>();
        for (ExperimentPhase phase : phases) {
            map.put(phase, new MetricsCollector());
        }
        return map;
    }

    private List<PhaseReport> buildPhaseReports(Map<ExperimentPhase, MetricsCollector> phaseMetricsCollectors) {
        List<PhaseReport> reports = new ArrayList<>();
        for (Map.Entry<ExperimentPhase, MetricsCollector> entry : phaseMetricsCollectors.entrySet()) {
            ExperimentPhase phase = entry.getKey();
            reports.add(new PhaseReport(phase.getName(), phase.getType(), entry.getValue().snapshot()));
        }
        return reports;
    }

    private static final class MetricsCollector {
        private final List<Long> latenciesMs = new CopyOnWriteArrayList<>();
        private long totalOperations;
        private long successfulOperations;
        private long failedOperations;
        private long totalLatencyMs;
        private long maxLatencyMs;
        private final Set<String> seenOrderIds = new HashSet<>();
        private long uniqueOrderIds;
        private long duplicateOrderIds;

        synchronized void record(long latencyMs, boolean success, String orderId) {
            totalOperations++;
            totalLatencyMs += latencyMs;
            latenciesMs.add(latencyMs);
            if (latencyMs > maxLatencyMs) {
                maxLatencyMs = latencyMs;
            }
            if (success) {
                successfulOperations++;
                if (orderId != null && !orderId.isBlank()) {
                    if (seenOrderIds.add(orderId)) {
                        uniqueOrderIds++;
                    } else {
                        duplicateOrderIds++;
                    }
                }
            } else {
                failedOperations++;
            }
        }

        synchronized ExperimentMetrics snapshot() {
            List<Long> sortedLatencies = new ArrayList<>(latenciesMs);
            sortedLatencies.sort(Long::compareTo);
            double errorRate = totalOperations == 0 ? 0.0 : (double) failedOperations / totalOperations;
            double avgLatency = totalOperations == 0 ? 0.0 : (double) totalLatencyMs / totalOperations;
            double p95 = percentile(sortedLatencies, 0.95);
            return new ExperimentMetrics(
                    totalOperations,
                    successfulOperations,
                    failedOperations,
                    errorRate,
                    p95,
                    avgLatency,
                    maxLatencyMs,
                    uniqueOrderIds,
                    duplicateOrderIds,
                    sortedLatencies
            );
        }

        private double percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) {
                return 0.0;
            }
            int index = (int) Math.ceil(percentile * values.size()) - 1;
            if (index < 0) {
                index = 0;
            }
            if (index >= values.size()) {
                index = values.size() - 1;
            }
            return values.get(index);
        }
    }
}
