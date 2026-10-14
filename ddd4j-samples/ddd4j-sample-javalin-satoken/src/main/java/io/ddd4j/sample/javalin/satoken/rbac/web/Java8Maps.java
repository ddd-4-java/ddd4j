/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.sample.javalin.satoken.rbac.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Java 8 equivalent of the small immutable-map literals used by the sample responses.
 */
final class Java8Maps {

    private Java8Maps() {
    }

    static Map<String, Object> of(Object... keyValues) {
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
