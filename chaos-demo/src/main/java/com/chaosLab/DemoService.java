package com.chaosLab;

import org.springframework.stereotype.Service;

@Service
public class DemoService {

    @Chaosify(scenario = "default")
    public String unstableOperation() {
        simulateWork(200);
        return "Default chaos applied";
    }

    @Chaosify(scenario = "stress")
    public String stressOperation() {
        simulateCpuWork(300);
        return "Stress chaos applied";
    }

    private void simulateWork(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    private void simulateCpuWork(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            Math.sqrt(Math.random());
        }
    }

}