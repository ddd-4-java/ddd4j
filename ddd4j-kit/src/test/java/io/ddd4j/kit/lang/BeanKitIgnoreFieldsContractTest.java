/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证三线 Bean 属性忽略列表的共同契约。
 */
class BeanKitIgnoreFieldsContractTest {

    /**
     * 验证重复指定同一个忽略字段时仍可生成 Map，且姓名字段被排除。
     */
    @Test
    void duplicateIgnoreFieldDoesNotRejectConversion() {
        Map<String, Object> values = BeanKit.toMap(new Person(), true, "name", "name");
        assertThat(values).doesNotContainKey("name").containsEntry("age", 30);
    }

    /**
     * 验证忽略列表中的空引用不会阻断转换，其他忽略项仍然生效。
     */
    @Test
    void nullIgnoreEntryDoesNotRejectConversion() {
        Map<String, Object> values = BeanKit.toMap(new Person(), true, "name", null);
        assertThat(values).doesNotContainKey("name").containsEntry("age", 30);
    }

    /**
     * 具备姓名和年龄属性的 Bean 转 Map 样本。
     */
    private static final class Person {
        /**
         * 夹具中用于验证字段忽略规则的姓名。
         */
        private final String name = "alice";
        /**
         * 用来验证其他字段仍保留的年龄。
         */
        private final int age = 30;
    }
}
