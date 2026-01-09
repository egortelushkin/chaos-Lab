package com.helloegor03;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChaosEngine {

    private final List<ChaosRule> rules = new ArrayList<>();

    public ChaosEngine addRule(ChaosRule rule) {
        rules.add(rule);
        return this;
    }

    public void unleash() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ChaosRule rule : rules) {
            if (rule.shouldApply()) {
                System.out.println("Applying chaos: " + rule.getEffect().getClass().getSimpleName());
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        rule.apply();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
        }
        // Ждём, пока все эффекты завершатся
        futures.forEach(CompletableFuture::join);
    }
}