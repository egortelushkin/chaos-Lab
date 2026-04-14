package com.chaosLab;

public class Chaos {
    public static ChaosBuilder builder() {
        return new ChaosBuilder();
    }

    public static ChaosExperimentBuilder experiment(String name) {
        return new ChaosExperimentBuilder(name);
    }
}
