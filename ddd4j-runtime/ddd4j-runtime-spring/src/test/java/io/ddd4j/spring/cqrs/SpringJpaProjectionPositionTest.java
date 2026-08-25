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
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SpringJpaProjectionPosition} 不可变契约验收测试。
 * <p>
 * 验证 {@link ProjectionPosition#withNextEventNumber(long)} 返回新实例且不修改原对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@DisplayName("SpringJpaProjectionPosition 不可变契约验收")
class SpringJpaProjectionPositionTest {

    @Test
    @DisplayName("withNextEventNumber 应返回新实例，不修改原对象")
    void withNextEventNumberShouldReturnNewInstance() {
        SpringJpaProjectionPosition original = new SpringJpaProjectionPosition("stream-1", 0L);

        ProjectionPosition updated = original.withNextEventNumber(5L);

        // 原对象不变
        assertEquals(0L, original.getNextEventNumber(),
                "原对象的 nextEventNumber 不应被修改");
        assertEquals("stream-1", original.getStreamId(),
                "原对象的 streamId 不应被修改");

        // 新对象包含新值
        assertNotSame(original, updated, "withNextEventNumber 应返回新对象实例");
        assertEquals(5L, updated.getNextEventNumber(),
                "新对象的 nextEventNumber 应为传入值");
        assertEquals("stream-1", updated.getStreamId(),
                "新对象的 streamId 应与原对象一致");
    }

    @Test
    @DisplayName("withNextEventNumber 连续调用应各自产生独立实例")
    void consecutiveCallsShouldProduceIndependentInstances() {
        SpringJpaProjectionPosition original = new SpringJpaProjectionPosition("stream-1", 0L);

        ProjectionPosition first = original.withNextEventNumber(1L);
        ProjectionPosition second = original.withNextEventNumber(2L);

        assertNotSame(first, second, "连续调用应返回不同实例");
        assertEquals(0L, original.getNextEventNumber(), "原对象应始终不变");
        assertEquals(1L, first.getNextEventNumber());
        assertEquals(2L, second.getNextEventNumber());
    }

    @Test
    @DisplayName("withNextEventNumber 返回类型应可安全转型为 SpringJpaProjectionPosition")
    void returnTypeShouldBeSpringJpaProjectionPosition() {
        SpringJpaProjectionPosition original = new SpringJpaProjectionPosition("stream-1", 0L);
        ProjectionPosition updated = original.withNextEventNumber(10L);

        assertInstanceOf(SpringJpaProjectionPosition.class, updated,
                "返回类型应为 SpringJpaProjectionPosition 以支持 JPA merge");
    }
}
