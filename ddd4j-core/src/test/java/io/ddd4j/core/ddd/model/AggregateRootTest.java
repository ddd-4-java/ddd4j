package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregateRootTest {
    @Test
    void shouldCollectNewEventsButNotReplayHistory() {
        TestAggregate aggregate = new TestAggregate();
        TestEvent created = new TestEvent();
        aggregate.record(created);
        assertEquals(1, aggregate.getUncommittedChanges().size());
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(0, aggregate.getUncommittedChanges().size());
    }
    @Test
    void shouldDispatchApplyAndReplayToEventHandler() {
        HandlingAggregate aggregate = new HandlingAggregate();
        aggregate.record(new TestEvent());
        assertEquals(1, aggregate.handled);
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(2, aggregate.handled);
        assertThrows(IllegalStateException.class, () -> new AggregateRoot<StringEntityId>() { }.apply(new TestEvent()));
    }
    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        void record(TestEvent event) { apply(event); }
        @EventHandler private void on(TestEvent event) { }
    }
    private static final class TestEvent extends DomainEvent<StringEntityId> {
        TestEvent() { super(new EntityIdPath(new StringEntityId("order-1"))); }
    }
    private static final class HandlingAggregate extends AggregateRoot<StringEntityId> {
        private int handled;
        void record(TestEvent event) { apply(event); }
        @EventHandler private void on(TestEvent event) { handled++; }
    }
}
