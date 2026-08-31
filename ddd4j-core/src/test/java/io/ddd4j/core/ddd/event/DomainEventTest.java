package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainEventTest {
    @Test
    void shouldCreateEventMetadataAndCausationChain() {
        TestId id = new TestId("order-1");
        TestEvent causing = new TestEvent(new EntityIdPath(id));
        TestEvent event = new TestEvent(new EntityIdPath(id), causing);
        assertNotNull(event.getEventId());
        assertNotNull(event.getEventTimestamp());
        assertEquals(id, event.getEntityId());
        assertEquals(causing.getEventId(), event.getCausationId());
        assertEquals(causing.getEventId(), event.getCorrelationId());
    }
    private static final class TestId implements AggregateRootId {
        private final String value; private TestId(String value) { this.value = value; }
        @Override public EntityType getType() { return new StringEntityType("Order"); }
        @Override public String asString() { return value; }
        @Override public String asTypedString() { return getType().asString() + ":" + value; }
    }
    private static final class TestEvent extends DomainEvent<TestId> {
        private TestEvent(EntityIdPath path) { super(path); }
        private TestEvent(EntityIdPath path, Event causing) { super(path, causing); }
    }
}
