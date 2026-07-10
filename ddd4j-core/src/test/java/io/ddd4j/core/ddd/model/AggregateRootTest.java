package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.core.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AggregateRootTest {

    @Test
    void shouldBufferPullAndClearDomainEvents() {
        TestAggregate aggregate = new TestAggregate("order-1");

        aggregate.rename("new-name");

        assertTrue(aggregate.hasDomainEvents());
        assertEquals(1, aggregate.domainEvents().size());

        List<DomainEvent<?>> events = aggregate.pullDomainEvents();

        assertEquals(1, events.size());
        assertFalse(aggregate.hasDomainEvents());
        assertTrue(events.get(0) instanceof TestDomainEvent);
        assertEquals("new-name", ((TestDomainEvent) events.get(0)).getName());
    }

    @Test
    void shouldRejectNullDomainEvent() {
        TestAggregate aggregate = new TestAggregate("order-1");

        assertThrows(NullPointerException.class, aggregate::raiseNullEvent);
    }

    private static final class TestAggregate extends AggregateRoot<String> {

        private final String id;

        private TestAggregate(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        private void rename(String name) {
            registerEvent(new TestDomainEvent(name));
        }

        private void raiseNullEvent() {
            registerEvent(null);
        }
    }

    private static final class TestDomainEvent extends DomainEvent<OrderId> {

        private final String name;

        private TestDomainEvent(String name) {
            super(new EntityIdPath(new OrderId(name)));
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public EventType getEventType() {
            return null;
        }
    }

    private static final class OrderId implements org.fuin.ddd4j.core.AggregateRootId {

        private static final org.fuin.ddd4j.core.EntityType TYPE = new TestEntityType("Order");

        private final String value;

        OrderId(String value) {
            this.value = value;
        }

        @Override
        public org.fuin.ddd4j.core.EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + " " + value;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof OrderId)) {
                return false;
            }
            return value.equals(((OrderId) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static final class TestEntityType implements org.fuin.ddd4j.core.EntityType {

        private final String name;

        TestEntityType(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }
    }
}