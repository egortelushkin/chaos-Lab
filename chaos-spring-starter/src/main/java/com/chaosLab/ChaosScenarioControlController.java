package com.chaosLab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/chaos/control/scenarios")
@ConditionalOnProperty(prefix = "chaos.control", name = "enabled", havingValue = "true")
public class ChaosScenarioControlController {

    private final ChaosScenarioControlService controlService;

    public ChaosScenarioControlController(ChaosScenarioControlService controlService) {
        this.controlService = Objects.requireNonNull(controlService, "controlService must not be null");
    }

    @GetMapping
    public List<ChaosScenarioControlService.ScenarioState> listScenarios() {
        return controlService.list();
    }

    @PostMapping("/{name}/enable")
    public ResponseEntity<ChaosScenarioControlService.ControlResult> enableScenario(@PathVariable String name) {
        boolean enabled = controlService.enable(name);
        if (!enabled) {
            return ResponseEntity.status(404).body(ChaosScenarioControlService.ControlResult.error(
                    "Scenario not found: " + name,
                    0,
                    Map.of("scenario", name)
            ));
        }
        return ResponseEntity.ok(ChaosScenarioControlService.ControlResult.ok(
                "Scenario enabled: " + name,
                1,
                Map.of("scenario", name)
        ));
    }

    @PostMapping("/{name}/disable")
    public ResponseEntity<ChaosScenarioControlService.ControlResult> disableScenario(@PathVariable String name) {
        boolean disabled = controlService.disable(name);
        if (!disabled) {
            return ResponseEntity.status(404).body(ChaosScenarioControlService.ControlResult.error(
                    "Scenario not found: " + name,
                    0,
                    Map.of("scenario", name)
            ));
        }
        return ResponseEntity.ok(ChaosScenarioControlService.ControlResult.ok(
                "Scenario disabled: " + name,
                1,
                Map.of("scenario", name)
        ));
    }

    @PostMapping("/disable-all")
    public ResponseEntity<ChaosScenarioControlService.ControlResult> disableAllScenarios() {
        int affected = controlService.disableAll();
        return ResponseEntity.ok(ChaosScenarioControlService.ControlResult.ok(
                "All scenarios disabled",
                affected,
                Map.of()
        ));
    }

    @PostMapping("/enable-only")
    public ResponseEntity<ChaosScenarioControlService.ControlResult> enableOnlyScenarios(
            @RequestBody EnableOnlyRequest request
    ) {
        List<String> names = request == null || request.names() == null ? List.of() : request.names();
        int affected = controlService.enableOnly(names);
        return ResponseEntity.ok(ChaosScenarioControlService.ControlResult.ok(
                "Enabled only requested scenarios",
                affected,
                Map.of("names", names)
        ));
    }

    public record EnableOnlyRequest(List<String> names) {
    }
}
