package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.model.AggregateRoot;
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
        assertEquals("new-name", events.get(0).source());
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

    private static final class TestDomainEvent extends DomainEvent<String> {

        private TestDomainEvent(String source) {
            super(source);
        }
    }
}
