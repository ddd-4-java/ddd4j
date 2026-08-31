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
        assertThrows(IllegalStateException.class, () -> new AggregateRoot<StringEntityId>() {
            @Override public StringEntityId id() { return new StringEntityId("missing"); }
        }.apply(new TestEvent()));
    }
    @Test
    void shouldExposeDomainIdentityAndCompareEntitiesById() {
        IdentityAggregate first = new IdentityAggregate(new StringEntityId("same"));
        IdentityAggregate second = new IdentityAggregate(new StringEntityId("same"));
        assertEquals(first.id(), second.id());
        assertEquals(true, first.sameIdentityAs(second));
    }
    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        void record(TestEvent event) { apply(event); }
        @EventHandler private void on(TestEvent event) { }
        @Override public StringEntityId id() { return new StringEntityId("order-1"); }
    }
    private static final class TestEvent extends DomainEvent<StringEntityId> {
        TestEvent() { super(new EntityIdPath(new StringEntityId("order-1"))); }
    }
    private static final class HandlingAggregate extends AggregateRoot<StringEntityId> {
        private int handled;
        void record(TestEvent event) { apply(event); }
        @EventHandler private void on(TestEvent event) { handled++; }
        @Override public StringEntityId id() { return new StringEntityId("order-1"); }
    }
    private static final class IdentityAggregate extends AggregateRoot<StringEntityId> {
        private final StringEntityId id;
        private IdentityAggregate(StringEntityId id) { this.id = id; }
        @Override public StringEntityId id() { return id; }
    }
}
