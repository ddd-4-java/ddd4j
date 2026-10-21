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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link InMemoryEventStore} 测试（继承契约测试并验证内存实现特有行为）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DisplayName("InMemoryEventStore")
class InMemoryEventStoreTest extends EventStoreContractTest {

    /**
     * 创建内存事件存储实现供通用契约测试使用。
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
