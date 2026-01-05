package com.helloegor03;

import org.apache.kafka.clients.consumer.*;

import java.time.Duration;

public class ChaosKafkaConsumer<K, V> {

    private final Consumer<K, V> delegate;
    private final ChaosKafkaInterceptor chaos;

    public ChaosKafkaConsumer(Consumer<K, V> delegate, ChaosKafkaInterceptor chaos) {
        this.delegate = delegate;
        this.chaos = chaos;
    }

    public ConsumerRecords<K, V> poll(Duration timeout) {
        chaos.beforeConsume();
        return delegate.poll(timeout);
    }
}