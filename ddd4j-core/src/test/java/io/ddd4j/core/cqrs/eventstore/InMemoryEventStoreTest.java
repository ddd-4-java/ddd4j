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

    private DomainEvent<TestId> eventWithNestedAppend(TestId id, Runnable nestedAppend) {
        return new DomainEvent<TestId>(new EntityIdPath(id)) {
            @Override
            public EventId getCorrelationId() {
                nestedAppend.run();
                return super.getCorrelationId();
            }
        };
    }

    private static final class TestId implements AggregateRootId {
        private final String value;

        private TestId(String value) {
            this.value = value;
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

    private static final class TestEvent extends DomainEvent<TestId> {
        private TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }
    }
}
