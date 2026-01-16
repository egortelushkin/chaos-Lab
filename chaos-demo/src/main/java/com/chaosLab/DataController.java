package com.chaosLab;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
//  chaor core example of adding chaos to a data endpoint
@RestController
public class DataController {

    @GetMapping("/data")
    public List<String> getData() {
        // Throw chaos effects if configured
        ChaosScenario scenario = ChaosScenarios.get("DataChaos");
        if (scenario != null) {
            scenario.unleash();
        }

        // Then return some dummy data
        return List.of("Item 1", "Item 2", "Item 3");
    }
}