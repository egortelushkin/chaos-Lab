package com.chaosLab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//chaos core configuration
//u can customize chaos scenarios here without spring core or spring starter dependencies
@Configuration
public class ChaosConfig {

    @Bean
    public ChaosScenario dataChaosScenario() {
        ChaosScenario scenario = Chaos.builder()
                .delay(500).probability(0.3)    // 30% chance of delay up to 500ms
                .exception().probability(0.1)   // 10% chance of throwing an exception
                .scenario("DataChaos");
        ChaosScenarios.register(scenario);
        return scenario;
    }
}