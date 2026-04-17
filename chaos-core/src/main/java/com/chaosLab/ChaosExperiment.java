package com.chaosLab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class ChaosExperiment {

    private final String name;
    private final int virtualUsers;
    private final int workerThreads;
    private final long thinkTimeMs;
    private final Long seed;
    private final Supplier<SyntheticUser> userFactory;
    private final ChaosEngine faultEngine;
    private final Set<String> faultTargetOperations;
    private final List<ExperimentPhase> phases;
    private final List<Invariant> invariants;

    ChaosExperiment(
            String name,
            int virtualUsers,
            int workerThreads,
            long thinkTimeMs,
            Long seed,
            Supplier<SyntheticUser> userFactory,
            ChaosEngine faultEngine,
            Set<String> faultTargetOperations,
            List<ExperimentPhase> phases,
            List<Invariant> invariants
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.virtualUsers = virtualUsers;
        this.workerThreads = workerThreads;
        this.thinkTimeMs = thinkTimeMs;
        this.seed = seed;
        this.userFactory = Objects.requireNonNull(userFactory, "userFactory must not be null");
        this.faultEngine = faultEngine;
        Objects.requireNonNull(faultTargetOperations, "faultTargetOperations must not be null");
        this.faultTargetOperations = Collections.unmodifiableSet(new LinkedHashSet<>(faultTargetOperations));
        this.phases = Collections.unmodifiableList(new ArrayList<>(phases));
        this.invariants = Collections.unmodifiableList(new ArrayList<>(invariants));
    }

    public String getName() {
        return name;
    }

    public int getVirtualUsers() {
        return virtualUsers;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public long getThinkTimeMs() {
        return thinkTimeMs;
    }

    public Long getSeed() {
        return seed;
    }

    public Supplier<SyntheticUser> getUserFactory() {
        return userFactory;
    }

    public ChaosEngine getFaultEngine() {
        return faultEngine;
    }

    public Set<String> getFaultTargetOperations() {
        return faultTargetOperations;
    }

    public List<ExperimentPhase> getPhases() {
        return phases;
    }

    public List<Invariant> getInvariants() {
        return invariants;
    }

    public ExperimentReport run() {
        return new ChaosExperimentRunner().run(this);
    }
}
