package com.helloegor03;

import java.util.ArrayList;
import java.util.List;

public class ChaosEngine {

    private final List<ChaosRule> rules = new ArrayList<>();

    public ChaosEngine addRule(ChaosRule rule) {
        rules.add(rule);
        return this;
    }

    public void unleash() {
        for (ChaosRule rule : rules) {
            if (rule.shouldApply()) {
                try {
                    rule.apply();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}