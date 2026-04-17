package com.chaosLab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChaosSpringConfig {

    public static final String DEFAULT_SCENARIO = "default";
    public static final String STRESS_SCENARIO = "stress";
    public static final String LEGACY_DEFAULT_SCENARIO = "DefaultChaosScenario";
    public static final String LEGACY_STRESS_SCENARIO = "StressChaos";

    @Bean
    public ChaosScenario defaultChaosScenario() {
        ChaosScenario scenario = Chaos.builder()
                .delay(500).probability(0.3)
                .exception().probability(0.1)
                .scenario(DEFAULT_SCENARIO);
        ChaosScenarios.register(scenario);
        ChaosScenarios.registerAlias(LEGACY_DEFAULT_SCENARIO, scenario);
        return scenario;
    }

    @Bean
    public ChaosScenario stressChaosScenario() {
        ChaosScenario scenario = Chaos.builder()
                .delay(1000).probability(0.5)
                .exception().probability(0.2)
                .scenario(STRESS_SCENARIO);

        // custom effect for chaosRule
        scenario.getEngine().addRule(new ChaosRule(1.0, new CpuSpikeEffect(100)));
        scenario.getEngine().addRule(new ChaosRule(1.0, new MemoryLeakEffect(1024*1024*5)));
        scenario.getEngine().addRule(new ChaosRule(0.3, new PartialExceptionEffect(0.3)));

        ChaosScenarios.register(scenario);
        ChaosScenarios.registerAlias(LEGACY_STRESS_SCENARIO, scenario);
        return scenario;
    }
}
