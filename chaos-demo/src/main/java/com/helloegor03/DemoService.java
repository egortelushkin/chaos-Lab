package com.helloegor03;

import org.springframework.stereotype.Service;

@Service
public class DemoService {

    @Chaosify(scenario = "default")
    public String unstableOperation() {
        return "Default chaos applied";
    }

    @Chaosify(scenario = "stress")
    public String stressOperation() {
        return "Stress chaos applied";
    }
}