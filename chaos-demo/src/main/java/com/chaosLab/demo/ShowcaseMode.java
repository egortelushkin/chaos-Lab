package com.chaosLab.demo;

import java.util.Locale;

public enum ShowcaseMode {
    QUICK,
    FULL;

    public static ShowcaseMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return QUICK;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "QUICK" -> QUICK;
            case "FULL" -> FULL;
            default -> throw new IllegalArgumentException("Unsupported mode: " + raw + ". Allowed values: quick, full");
        };
    }
}
