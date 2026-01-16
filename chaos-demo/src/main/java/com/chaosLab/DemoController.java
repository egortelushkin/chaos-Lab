package com.chaosLab;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/default")
    public String defaultChaos() {
        return demoService.unstableOperation();
    }

    @GetMapping("/stress")
    public String stressChaos() {
        return demoService.stressOperation();
    }
}