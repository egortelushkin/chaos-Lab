package com.chaosLab;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class ChaosExperimentBuilder {

    private final String name;
    private int virtualUsers = 1;
    private int workerThreads = 1;
    private long thinkTimeMs = 0L;
    private Long seed;
    private Supplier<SyntheticUser> userFactory;
    private ChaosEngine faultEngine;
    private final Set<String> faultTargetOperations = new LinkedHashSet<>();
    private final List<ExperimentPhase> phases = new ArrayList<>();
    private final List<Invariant> invariants = new ArrayList<>();

    ChaosExperimentBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public ChaosExperimentBuilder virtualUsers(int virtualUsers) {
        if (virtualUsers <= 0) {
            throw new IllegalArgumentException("virtualUsers must be > 0");
        }
        this.virtualUsers = virtualUsers;
        if (workerThreads > virtualUsers) {
            workerThreads = virtualUsers;
        }
        return this;
    }

    public ChaosExperimentBuilder workerThreads(int workerThreads) {
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("workerThreads must be > 0");
        }
        this.workerThreads = workerThreads;
        return this;
    }

    public ChaosExperimentBuilder thinkTime(Duration thinkTime) {
        Objects.requireNonNull(thinkTime, "thinkTime must not be null");
        if (thinkTime.isNegative()) {
            throw new IllegalArgumentException("thinkTime must be >= 0");
        }
        this.thinkTimeMs = thinkTime.toMillis();
        return this;
    }

    public ChaosExperimentBuilder withSeed(long seed) {
        this.seed = seed;
        return this;
    }

    public ChaosExperimentBuilder users(Supplier<SyntheticUser> userFactory) {
        this.userFactory = Objects.requireNonNull(userFactory, "userFactory must not be null");
        return this;
    }

    public ChaosExperimentBuilder faultEngine(ChaosEngine faultEngine) {
        this.faultEngine = faultEngine;
        return this;
    }

    public ChaosExperimentBuilder faultTargetOperations(List<String> operations) {
        Objects.requireNonNull(operations, "operations must not be null");
        for (String operation : operations) {
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("fault target operation must not be blank");
            }
            faultTargetOperations.add(operation.trim());
        }
        return this;
    }

    public ChaosExperimentBuilder faultTargetOperations(String... operations) {
        Objects.requireNonNull(operations, "operations must not be null");
        return faultTargetOperations(List.of(operations));
    }

    public ChaosExperimentBuilder phase(String name, PhaseType type, Duration duration) {
        phases.add(new ExperimentPhase(name, type, duration));
        return this;
    }

    public ChaosExperimentBuilder warmup(Duration duration) {
        return phase("warmup", PhaseType.WARMUP, duration);
    }

    public ChaosExperimentBuilder fault(Duration duration) {
        return phase("fault", PhaseType.FAULT, duration);
    }

    public ChaosExperimentBuilder recovery(Duration duration) {
        return phase("recovery", PhaseType.RECOVERY, duration);
    }

    public ChaosExperimentBuilder invariant(Invariant invariant) {
        invariants.add(Objects.requireNonNull(invariant, "invariant must not be null"));
        return this;
    }

    public ChaosExperimentBuilder maxErrorRate(double maxErrorRate) {
        invariants.add(new ErrorRateInvariant(maxErrorRate));
        return this;
    }

    public ChaosExperimentBuilder maxP95LatencyMs(double maxP95LatencyMs) {
        invariants.add(new P95LatencyInvariant(maxP95LatencyMs));
        return this;
    }

    public ChaosExperimentBuilder noDuplicateOrderIds() {
        invariants.add(new NoDuplicateOrderIdsInvariant());
        return this;
    }

    public ChaosExperiment build() {
        if (userFactory == null) {
            throw new IllegalStateException("users(...) must be configured");
        }
        if (phases.isEmpty()) {
            throw new IllegalStateException("at least one phase must be configured");
        }
        if (workerThreads > virtualUsers) {
            workerThreads = virtualUsers;
        }
        return new ChaosExperiment(
                name,
                virtualUsers,
                workerThreads,
                thinkTimeMs,
                seed,
                userFactory,
                faultEngine,
                faultTargetOperations,
                phases,
                invariants
        );
    }
}
