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

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
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

        private final String value;

        private TestAggregateRootId(String value) {
            this.value = Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public EntityType getType() {
            return new StringEntityType("Order");
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return "Order:" + value;
        }
    }

    /**
     * 无因果源的根事件。
     */
    private static final class RootEvent extends DomainEvent<TestAggregateRootId> {

        private RootEvent(TestAggregateRootId aggregateId) {
            super(new EntityIdPath(aggregateId));
        }
    }

    /**
     * 由前置事件触发的派生事件（respondTo 构造语义）。
     */
    private static final class DerivedEvent extends DomainEvent<TestAggregateRootId> {

        private DerivedEvent(TestAggregateRootId aggregateId, DomainEvent<?> respondTo) {
            super(new EntityIdPath(new StringEntityId(aggregateId.asString())), respondTo);
        }
    }
}
