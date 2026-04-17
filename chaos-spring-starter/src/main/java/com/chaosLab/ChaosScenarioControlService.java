package com.chaosLab;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChaosScenarioControlService {

    public List<ScenarioState> list() {
        return ChaosScenarios.all().entrySet().stream()
                .map(entry -> new ScenarioState(entry.getKey(), entry.getValue().isEnabled()))
                .toList();
    }

    public boolean enable(String name) {
        return ChaosScenarios.enable(name);
    }

    public boolean disable(String name) {
        return ChaosScenarios.disable(name);
    }

    public int disableAll() {
        return ChaosScenarios.disableAll();
    }

    public int enableOnly(Collection<String> names) {
        Objects.requireNonNull(names, "names must not be null");
        return ChaosScenarios.enableOnly(names);
    }

    public record ScenarioState(String name, boolean enabled) {
    }

    public record ControlResult(boolean success, String message, int affectedScenarios, Map<String, Object> details) {
        public static ControlResult ok(String message, int affectedScenarios, Map<String, Object> details) {
            return new ControlResult(true, message, affectedScenarios, details);
        }

        public static ControlResult error(String message, int affectedScenarios, Map<String, Object> details) {
            return new ControlResult(false, message, affectedScenarios, details);
        }
    }
}
