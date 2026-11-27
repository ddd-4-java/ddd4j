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

    /** \u9a8c\u8bc1\u91cd\u590d\u6307\u5b9a\u540c\u4e00\u4e2a\u5ffd\u7565\u5b57\u6bb5\u65f6\u4ecd\u53ef\u751f\u6210 Map\uff0c\u4e14\u59d3\u540d\u5b57\u6bb5\u88ab\u6392\u9664\u3002 */
    @Test
    void duplicateIgnoreFieldDoesNotRejectConversion() {
        Map<String, Object> values = BeanKit.toMap(new Person(), true, "name", "name");
        assertThat(values).doesNotContainKey("name").containsEntry("age", 30);
    }

    /** \u9a8c\u8bc1\u5ffd\u7565\u5217\u8868\u4e2d\u7684\u7a7a\u5f15\u7528\u4e0d\u4f1a\u963b\u65ad\u8f6c\u6362\uff0c\u5176\u4ed6\u5ffd\u7565\u9879\u4ecd\u7136\u751f\u6548\u3002 */
    @Test
    void nullIgnoreEntryDoesNotRejectConversion() {
        Map<String, Object> values = BeanKit.toMap(new Person(), true, "name", null);
        assertThat(values).doesNotContainKey("name").containsEntry("age", 30);
    }

    /** \u5177\u5907\u59d3\u540d\u548c\u5e74\u9f84\u5c5e\u6027\u7684 Bean \u8f6c Map \u6837\u672c\u3002 */
    private static final class Person {
        /** \u5939\u5177\u4e2d\u7528\u4e8e\u9a8c\u8bc1\u5b57\u6bb5\u5ffd\u7565\u89c4\u5219\u7684\u59d3\u540d\u3002 */
        private final String name = "alice";
        /** \u7528\u6765\u9a8c\u8bc1\u5176\u4ed6\u5b57\u6bb5\u4ecd\u4fdd\u7559\u7684\u5e74\u9f84\u3002 */
        private final int age = 30;
    }
}
