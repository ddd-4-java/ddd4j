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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 事件重放因果保留行为测试。
 *
 * <p>对应 OpenSpec change {@code promote-causality-to-broker-headers}，
 * 派生自家族规格 Requirement: Replay preserves causality：
 * 重放或补偿触发的派生事件 MUST 保留原 correlationId，并以重放源事件为 causationId。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ReplayCausalityPreservedMustTest {

    /** \u9a8c\u8bc1<code>replayDerived.getCorrelationId().asString()</code> \u7b49\u4e8e <code>second.getCorrelationId().asString()</code>\uff0c<code>replayDerived.getCausationId().asString()</code> \u7b49\u4e8e <code>replayedSource.getEventId().asString()</code>\uff0c<code>replayedStored.causationId().asString()</code> \u7b49\u4e8e <code>second.getCausationId().asString()</code>\u3002 */
    @Test
    void derivedEventMustPreserveOriginalCorrelationAndPointToReplaySource() {
        InMemoryEventStore store = new InMemoryEventStore();
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-001");

        // 原始链路：根事件 → 派生事件（correlationId = 根事件 eventId）
        RootEvent original = new RootEvent(aggregateId);
        store.append("Order", aggregateId, Collections.<DomainEvent<?>>singletonList(original), 0);
        DerivedEvent second = new DerivedEvent(aggregateId, original);
        store.append("Order", aggregateId, Collections.<DomainEvent<?>>singletonList(second), 1);

        // 重放：从事件存储读回派生事件作为触发源
        StoredEvent replayedStored = store.read("Order", aggregateId).get(1);
        DomainEvent<?> replayedSource = replayedStored.payload();

        // 重放触发的新派生事件
        DerivedEvent replayDerived = new DerivedEvent(aggregateId, replayedSource);

        assertEquals(second.getCorrelationId().asString(),
                replayDerived.getCorrelationId().asString(),
                "重放派生事件必须保留原 correlationId");
        assertEquals(replayedSource.getEventId().asString(),
                replayDerived.getCausationId().asString(),
                "重放派生事件的 causationId 必须指向重放源事件");
        assertEquals(second.getCorrelationId().asString(),
                replayedStored.correlationId().asString(),
                "事件存储持久化的因果快照必须保留 correlationId");
        assertEquals(second.getCausationId().asString(),
                replayedStored.causationId().asString(),
                "事件存储持久化的因果快照必须保留 causationId");
    }

    /** \u9a8c\u8bc1<code>derived.getCorrelationId().asString()</code> \u7b49\u4e8e <code>source.getEventId().asString()</code>\uff0c<code>derived.getCausationId().asString()</code> \u7b49\u4e8e <code>source.getEventId().asString()</code>\u3002 */
    @Test
    void derivedEventWithoutCausalSourceMustFallBackToSourceEventId() {
        InMemoryEventStore store = new InMemoryEventStore();
        TestAggregateRootId aggregateId = new TestAggregateRootId("order-002");

        // 重放源本身没有 correlationId
        RootEvent source = new RootEvent(aggregateId);
        store.append("Order", aggregateId, Collections.<DomainEvent<?>>singletonList(source), 0);
        DomainEvent<?> replayedSource = store.read("Order", aggregateId).get(0).payload();

        DerivedEvent derived = new DerivedEvent(aggregateId, replayedSource);

        assertEquals(source.getEventId().asString(),
                derived.getCorrelationId().asString(),
                "触发源无 correlationId 时以触发源 eventId 兜底为 correlationId");
        assertEquals(source.getEventId().asString(), derived.getCausationId().asString());
    }

    // ========================= 测试辅助类 =========================

    /**
     * 测试用聚合根标识。
     */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        private TestAggregateRootId(String value) {
            this.value = Objects.requireNonNull(value, "value must not be null");
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
     * 无因果源的根事件。
     */
    private static final class RootEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>RootEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param aggregateId \u7528\u4e8e\u5b9a\u4f4d\u88ab\u6d4b\u805a\u5408\u7684\u6807\u8bc6
         */
        private RootEvent(TestAggregateRootId aggregateId) {
            super(new EntityIdPath(aggregateId));
        }
    }

    /**
     * 由前置事件触发的派生事件（respondTo 构造语义）。
     */
    private static final class DerivedEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>DerivedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param aggregateId \u7528\u4e8e\u5b9a\u4f4d\u88ab\u6d4b\u805a\u5408\u7684\u6807\u8bc6
         * @param respondTo \u7528\u4e8e\u521d\u59cb\u5316 <code>DerivedEvent</code> \u7684 <code>DomainEvent&lt;?&gt;</code> \u503c
         */
        private DerivedEvent(TestAggregateRootId aggregateId, DomainEvent<?> respondTo) {
            super(new EntityIdPath(new StringEntityId(aggregateId.asString())), respondTo);
        }
    }
}
