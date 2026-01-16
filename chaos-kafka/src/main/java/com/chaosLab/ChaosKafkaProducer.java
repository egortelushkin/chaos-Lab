package com.chaosLab;

import org.apache.kafka.clients.producer.*;

public class ChaosKafkaProducer<K, V> {

    private final Producer<K, V> delegate;
    private final ChaosKafkaInterceptor chaos;

    public ChaosKafkaProducer(Producer<K, V> delegate, ChaosKafkaInterceptor chaos) {
        this.delegate = delegate;
        this.chaos = chaos;
    }

    public void send(ProducerRecord<K, V> record) {
        chaos.beforeSend();
        delegate.send(record);
    }
}