/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.cqrs.eventstore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EventDeserializer 三层防御单元测试：
 * 1. 格式非法（{@link EventDeserializer#isValidClassName}）
 * 2. 白名单拒绝（{@link ClassNameFilter#allows(String)}）
 * 3. 类加载失败（{@code Class.forName} 抛 {@link ClassNotFoundException}）
 */
class EventDeserializerTest {

    @AfterEach
    void resetFilter() {
        EventDeserializer.setFilter(EventDeserializer.defaultFilter());
    }

    @Nested
    class ClassNameValidation {

        @Test
        void 合法全限定类名_返回true() {
            assertThat(EventDeserializer.isValidClassName("io.ddd4j.event.OrderCreated")).isTrue();
            assertThat(EventDeserializer.isValidClassName("com.example.$Inner$Class")).isTrue();
        }

        @Test
        void 空字符串或null_返回false() {
            assertThat(EventDeserializer.isValidClassName(null)).isFalse();
            assertThat(EventDeserializer.isValidClassName("")).isFalse();
        }

        @Test
        void 仅一个包段_返回false() {
            assertThat(EventDeserializer.isValidClassName("OrderCreated")).isFalse();
        }

        @Test
        void 包段以数字开头_返回false() {
            assertThat(EventDeserializer.isValidClassName("1io.ddd4j.Foo")).isFalse();
            assertThat(EventDeserializer.isValidClassName("io.1dd4j.Foo")).isFalse();
        }

        @Test
        void 含特殊字符_返回false() {
            assertThat(EventDeserializer.isValidClassName("io.ddd4j;exec.Foo")).isFalse();
            assertThat(EventDeserializer.isValidClassName("io.ddd4j.Foo Bar")).isFalse();
            assertThat(EventDeserializer.isValidClassName("io.ddd4j.Foo[]")).isFalse();
        }
    }

    @Nested
    class DeserializeFallback {

        @Test
        void 类名格式非法_回退Map() {
            Object result = EventDeserializer.deserialize("{\"a\":1}", "1io.bad.class");
            assertThat(result).isInstanceOf(Map.class);
            assertThat((Map<String, Object>) result).containsEntry("a", 1);
        }

        @Test
        void 类不存在_回退Map() {
            Object result = EventDeserializer.deserialize("{\"x\":\"y\"}", "io.ddd4j.NonexistentEventClass_12345");
            assertThat(result).isInstanceOf(Map.class);
            assertThat((Map<String, Object>) result).containsEntry("x", "y");
        }
    }

    @Nested
    class ClassNameFilterSPI {

        @Test
        void 默认过滤器_允许所有合法格式() {
            assertThat(EventDeserializer.defaultFilter().allows("io.ddd4j.X")).isTrue();
            assertThat(EventDeserializer.defaultFilter().allows("java.lang.String")).isTrue();
        }

        @Test
        void 注册白名单_拒绝不在白名单内的类名() {
            Set<String> allowedPrefixes = Set.of("io.ddd4j.", "com.example.");
            EventDeserializer.setFilter(className -> allowedPrefixes.stream()
                    .anyMatch(className::startsWith));

            Object allowed = EventDeserializer.deserialize("{\"k\":\"v\"}", "io.ddd4j.SomeEvent");
            assertThat(allowed).isInstanceOf(Map.class);
            assertThat((Map<String, Object>) allowed).containsEntry("k", "v");

            Object blocked = EventDeserializer.deserialize("{\"k\":\"v\"}", "java.lang.Runtime");
            assertThat(blocked).isInstanceOf(Map.class);
            assertThat((Map<String, Object>) blocked).containsEntry("k", "v");
        }

        @Test
        void 注册null_抛NullPointerException() {
            assertThatThrownBy(() -> EventDeserializer.setFilter(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}