package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryEventStoreTest {
    @Test void shouldAppendReadAndRejectWrongVersion() {
        InMemoryEventStore store = new InMemoryEventStore(); TestId id = new TestId("order-1");
        store.append("Order", id, Collections.<DomainEvent<?>>singletonList(new TestEvent(id)), 0);
        assertEquals(1, store.read("Order", id).size());
        assertThrows(IllegalStateException.class, () -> store.append("Order", id, Collections.<DomainEvent<?>>singletonList(new TestEvent(id)), 0));
    }
    private static final class TestId implements AggregateRootId { private final String value; private TestId(String value) { this.value = value; }
        @Override public EntityType getType() { return new StringEntityType("Order"); } @Override public String asString() { return value; } @Override public String asTypedString() { return "Order:" + value; } }
    private static final class TestEvent extends DomainEvent<TestId> { private TestEvent(TestId id) { super(new EntityIdPath(id)); } }
}
