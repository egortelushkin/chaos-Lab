package com.chaosLab;

public interface ChaosEffect {
    default void before() {}
    void apply() throws Exception;
    default void after() {}
}