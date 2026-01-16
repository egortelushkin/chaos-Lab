package com.chaosLab;

public class ExceptionEffect implements ChaosEffect {

    @Override
    public void apply() {
        throw new RuntimeException("Chaos injected failure");
    }
}