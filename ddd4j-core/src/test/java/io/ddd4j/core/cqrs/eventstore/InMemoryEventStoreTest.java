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

import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InMemoryEventStore} 测试（继承契约测试并验证内存实现特有行为）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DisplayName("InMemoryEventStore")
class InMemoryEventStoreTest extends EventStoreContractTest {

    /**
     * 创建内存事件存储实现供通用契约测试使用。
     *
     * @return 内存事件存储
     */
    @Override
    protected EventStore createEventStore() {
        return new InMemoryEventStore();
    }

    /**
     * 验证<code>events.size()</code> 等于 <code>2</code>，<code>events.get(0).version()</code> 等于 <code>1L</code>，<code>store.append("Order", id, Collections.&lt;DomainEvent&lt;?&gt;&gt;singletonList(new TestEvent(id)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>。
     */
    @Test
    void shouldAppendReadAndRejectWrongVersion() {
        InMemoryEventStore store = new InMemoryEventStore();
        TestId id = new TestId("order-1");
        store.append("Order", id, Collections.<DomainEvent<?>>singletonList(new TestEvent(id)), 0);
        store.append("Order", id, Collections.<DomainEvent<?>>singletonList(new TestEvent(id)), 1);
        List<StoredEvent> events = store.read("Order", id);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
        assertEquals(1, store.read("Order", id, 1, 1).size());
        assertEquals(2, store.readAll(0, 10).size());
        assertThrows(AggregateVersionConflictException.class, () -> store.append("Order", id, Collections.<DomainEvent<?>>singletonList(new TestEvent(id)), 0));
    }


    /**
     * 验证<code>store.append( "Order", id, Arrays.asList(new TestEvent(id), invalid), 1)</code> 抛出 <code>NullPointerException.class</code>，<code>store.read("Order", id).size()</code> 等于 <code>1</code>，<code>appended.eventId()</code> 等于 <code>retry.getEventId()</code>。
     */
    @Test
    void failedBatchPreservesCommittedEventsAndGlobalPosition() {
        InMemoryEventStore store = new InMemoryEventStore();
        TestId id = new TestId("order-1");
        TestEvent original = new TestEvent(id);
        store.append("Order", id, Collections.<DomainEvent<?>>singletonList(original), 0);
        DomainEvent<TestId> invalid = new DomainEvent<TestId>(new EntityIdPath(id)) {
            @Override
            public EventId getEventId() {
                return null;
            }
        };

        assertThrows(NullPointerException.class, () -> store.append(
                "Order", id, Arrays.asList(new TestEvent(id), invalid), 1));

        assertAll(
                () -> assertEquals(1, store.read("Order", id).size()),
                () -> assertEquals(1, store.readAll(0, 10).size()));

        TestEvent retry = new TestEvent(id);
        store.append("Order", id, Collections.<DomainEvent<?>>singletonList(retry), 1);
        StoredEvent appended = store.readAll(2, 1).get(0);
        assertEquals(2L, appended.position());
        assertEquals(2L, appended.version());
        assertEquals(retry.getEventId(), appended.eventId());
    }


    /**
     * 验证<code>all.size()</code> 等于 <code>2</code>，<code>all.get(0).position()</code> 等于 <code>1L</code>，<code>store.read("Order", outerId).get(0).version()</code> 等于 <code>1L</code>。
     */
    @Test
    void reentrantAppendToOtherStreamPreservesBothGlobalPositions() {
        InMemoryEventStore store = new InMemoryEventStore();
        TestId outerId = new TestId("order-outer");
        TestId innerId = new TestId("order-inner");
        TestEvent innerEvent = new TestEvent(innerId);
        DomainEvent<TestId> outerEvent = eventWithNestedAppend(outerId, () -> store.append(
                "Order", innerId, Collections.<DomainEvent<?>>singletonList(innerEvent), 0));

        store.append("Order", outerId, Collections.<DomainEvent<?>>singletonList(outerEvent), 0);

        List<StoredEvent> all = store.readAll(1, 10);
        assertEquals(2, all.size());
        assertEquals(1L, all.get(0).position());
        assertEquals(2L, all.get(1).position());
        assertEquals(innerEvent.getEventId(), all.get(0).eventId());
        assertEquals(outerEvent.getEventId(), all.get(1).eventId());
        assertEquals(1L, store.read("Order", innerId).get(0).version());
        assertEquals(1L, store.read("Order", outerId).get(0).version());
    }

    /**
     * 验证<code>store.append("Order", id, Collections.&lt;DomainEvent&lt;?&gt;&gt;singletonList(outerEvent), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>all.get(0).eventId()</code> 等于 <code>innerEvent.getEventId()</code>。
     */
    @Test
    void reentrantAppendToSameStreamCausesExpectedVersionConflict() {
        InMemoryEventStore store = new InMemoryEventStore();
        TestId id = new TestId("order-1");
        TestEvent innerEvent = new TestEvent(id);
        DomainEvent<TestId> outerEvent = eventWithNestedAppend(id, () -> store.append(
                "Order", id, Collections.<DomainEvent<?>>singletonList(innerEvent), 0));

        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> store.append("Order", id, Collections.<DomainEvent<?>>singletonList(outerEvent), 0));

        assertEquals(0L, conflict.expectedVersion());
        assertEquals(1L, conflict.actualVersion());
        assertEquals(1, store.read("Order", id).size());
        List<StoredEvent> all = store.readAll(1, 10);
        assertEquals(1, all.size());
        assertEquals(1L, all.get(0).position());
        assertEquals(innerEvent.getEventId(), all.get(0).eventId());
    }

    /**
     * 构造 <code>DomainEvent&lt;TestId&gt;</code>、<code>EntityIdPath</code> 作为当前测试的输入或结果。
     *
     * @param id           当前要查找的测试记录标识
     * @param nestedAppend 用于构造本次返回结果的 <code>Runnable</code> 输入
     * @return 提供给后续契约断言的 <code>DomainEvent&lt;TestId&gt;</code> 结果
     */
    private DomainEvent<TestId> eventWithNestedAppend(TestId id, Runnable nestedAppend) {
        return new DomainEvent<TestId>(new EntityIdPath(id)) {
            @Override
            public EventId getCorrelationId() {
                nestedAppend.run();
                return super.getCorrelationId();
            }
        };
    }

    /**
     * 为测试聚合提供独立标识的值对象。
     */
    private static final class TestId implements AggregateRootId {
        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        private TestId(String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 此操作产生的<code>EntityType</code>结果
         */
        @Override
        public EntityType getType() {
            return new StringEntityType("Order");
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * @return 实际返回<code>"Order:" + value</code>，供调用方取得当前值
         */
        @Override
        public String asTypedString() {
            return "Order:" + value;
        }
    }

    /**
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    private static final class TestEvent extends DomainEvent<TestId> {
        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        private TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }
    }
}
