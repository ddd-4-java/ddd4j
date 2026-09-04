package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryEventStoreTest {
    @Test void shouldAppendReadAndRejectWrongVersion() {
        InMemoryEventStore store = new InMemoryEventStore(); TestId id = new TestId("order-1");
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
    private static final class TestId implements AggregateRootId { private final String value; private TestId(String value) { this.value = value; }
        @Override public EntityType getType() { return new StringEntityType("Order"); } @Override public String asString() { return value; } @Override public String asTypedString() { return "Order:" + value; } }
    private static final class TestEvent extends DomainEvent<TestId> { private TestEvent(TestId id) { super(new EntityIdPath(id)); } }
}
