package com.chaosLab;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.Locale;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "chaos")
public class ChaosStarterConfig {

    private Map<String, ScenarioProperties> scenarios;

    public Map<String, ScenarioProperties> getScenarios() {
        return scenarios;
    }

    public void setScenarios(Map<String, ScenarioProperties> scenarios) {
        this.scenarios = scenarios;
    }

    @PostConstruct
    public void registerChaosScenarios() {
        if (scenarios == null) return;

        scenarios.forEach((name, props) -> {
            if (!props.isEnabled()) return;

            ChaosBuilder builder = Chaos.builder();

            if (props.getDelay() != null)
                builder.delay(props.getDelay().getMaxMs())
                        .probability(props.getDelay().getProbability());

            if (props.getException() != null)
                builder.exception()
                        .probability(props.getException().getProbability());

            ChaosScenario scenario = builder.scenario(name);

            // custom effects for this scenario
            if (props.getCpuSpike() != null)
                scenario.getEngine().addRule(new ChaosRule(1.0, new CpuSpikeEffect(props.getCpuSpike().getDurationMs())));
            if (props.getMemoryLeak() != null)
                scenario.getEngine().addRule(new ChaosRule(1.0, new MemoryLeakEffect(props.getMemoryLeak().getBytes())));

            ChaosScenarios.register(scenario);
            registerLegacyAliases(name, scenario);
        });
    }

    private static void registerLegacyAliases(String configuredName, ChaosScenario scenario) {
        if (configuredName == null) {
            return;
        }
        String normalized = configuredName.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "default" -> ChaosScenarios.registerAlias("DefaultChaosScenario", scenario);
            case "stress" -> ChaosScenarios.registerAlias("StressChaos", scenario);
            case "defaultchaosscenario" -> ChaosScenarios.registerAlias("default", scenario);
            case "stresschaos" -> ChaosScenarios.registerAlias("stress", scenario);
            default -> {
                // no-op
            }
        }
    }

    // ---------------- Properties ----------------
    public static class ScenarioProperties {
        private boolean enabled = true;
        private EffectProps delay;
        private EffectProps exception;
        private CpuSpikeProps cpuSpike;
        private MemoryLeakProps memoryLeak;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public EffectProps getDelay() { return delay; }
        public void setDelay(EffectProps delay) { this.delay = delay; }
        public EffectProps getException() { return exception; }
        public void setException(EffectProps exception) { this.exception = exception; }
        public CpuSpikeProps getCpuSpike() { return cpuSpike; }
        public void setCpuSpike(CpuSpikeProps cpuSpike) { this.cpuSpike = cpuSpike; }
        public MemoryLeakProps getMemoryLeak() { return memoryLeak; }
        public void setMemoryLeak(MemoryLeakProps memoryLeak) { this.memoryLeak = memoryLeak; }
    }

    public static class EffectProps {
        private double probability;
        private int maxMs;
        public double getProbability() { return probability; }
        public void setProbability(double probability) { this.probability = probability; }
        public int getMaxMs() { return maxMs; }
        public void setMaxMs(int maxMs) { this.maxMs = maxMs; }
    }

    public static class CpuSpikeProps {
        private int durationMs;
        public int getDurationMs() { return durationMs; }
        public void setDurationMs(int durationMs) { this.durationMs = durationMs; }
    }

    public static class MemoryLeakProps {
        private int bytes;
        public int getBytes() { return bytes; }
        public void setBytes(int bytes) { this.bytes = bytes; }
    }
}
