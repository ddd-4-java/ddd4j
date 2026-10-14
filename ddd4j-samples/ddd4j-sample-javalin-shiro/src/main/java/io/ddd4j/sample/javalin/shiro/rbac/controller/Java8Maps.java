/* Copyright (c) 2024-2026 ddd4j project. Licensed under the Apache License, Version 2.0. */
package io.ddd4j.sample.javalin.shiro.rbac.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Java 8 equivalent of the small map literals used by sample responses.
 */
public final class Java8Maps {
    private Java8Maps() {
    }

    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues length must be even");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            String key = (String) keyValues[index];
            Object value = keyValues[index + 1];
            if (Objects.isNull(key) || Objects.isNull(value)) {
                throw new NullPointerException("map keys and values must not be null");
            }
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("duplicate key: " + key);
            }
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }
}
