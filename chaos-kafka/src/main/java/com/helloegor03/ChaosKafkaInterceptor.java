package com.helloegor03;

public class ChaosKafkaInterceptor {

    private final ChaosEngine chaos;

    public ChaosKafkaInterceptor(ChaosEngine chaos) {
        this.chaos = chaos;
    }

    public void beforeSend() {
        chaos.unleash();
    }

    public void beforeConsume() {
        chaos.unleash();
    }
}