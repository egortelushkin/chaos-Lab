package com.helloegor03;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChaosSpringConfig {

    @Bean
    public ChaosScenario defaultChaosScenario() {
        ChaosScenario scenario = Chaos.builder()
                .delay(500).probability(0.3)
                .exception().probability(0.1)
                .scenario("DefaultChaosScenario");
        ChaosScenarios.register(scenario);
        return scenario;
    }

    @Bean
    public ChaosScenario stressChaosScenario() {
        ChaosScenario scenario = Chaos.builder()
                .delay(1000).probability(0.5)
                .exception().probability(0.2)
                .scenario("StressChaos");

        // кастомные эффекты через ChaosRule
        scenario.getEngine().addRule(new ChaosRule(1.0, new CpuSpikeEffect(100)));
        scenario.getEngine().addRule(new ChaosRule(1.0, new MemoryLeakEffect(1024*1024*5)));
        scenario.getEngine().addRule(new ChaosRule(0.3, new PartialExceptionEffect(0.3)));

        ChaosScenarios.register(scenario);
        return scenario;
    }
}
