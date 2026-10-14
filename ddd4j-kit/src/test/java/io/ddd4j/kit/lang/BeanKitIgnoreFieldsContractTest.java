/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证三线 Bean 属性忽略列表的共同契约。 */
class BeanKitIgnoreFieldsContractTest {

    @Test
    void duplicateIgnoreFieldDoesNotRejectConversion() {
        Map<String, Object> values = BeanKit.toMap(new Person(), true, "name", "name");
        assertThat(values).doesNotContainKey("name").containsEntry("age", 30);
    }

    @Test
    void nullIgnoreEntryDoesNotRejectConversion() {
        Map<String, Object> values = BeanKit.toMap(new Person(), true, "name", null);
        assertThat(values).doesNotContainKey("name").containsEntry("age", 30);
    }

    private static final class Person {
        private final String name = "alice";
        private final int age = 30;
    }
}
