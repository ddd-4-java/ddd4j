/* Copyright (c) 2024-2026 ddd4j project. Licensed under the Apache License, Version 2.0. */
package io.ddd4j.sample.javalin.shiro.rbac.controller;

import java.util.LinkedHashMap;
import java.util.Map;

/** Java 8 equivalent of the small map literals used by sample responses. */
public final class Java8Maps {
    private Java8Maps() { }

    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues length must be even");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
