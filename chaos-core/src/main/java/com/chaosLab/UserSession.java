package com.chaosLab;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class UserSession {

    private final int userId;
    private final long seed;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    UserSession(int userId, long seed) {
        this.userId = userId;
        this.seed = seed;
    }

    public int getUserId() {
        return userId;
    }

    public long getSeed() {
        return seed;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Attribute '" + key + "' is not of type " + type.getSimpleName());
        }
        return type.cast(value);
    }

    public void putAttribute(String key, Object value) {
        attributes.put(Objects.requireNonNull(key, "key must not be null"), value);
    }

    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }
}
