package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregateRootTest {
    @Test
    void shouldCollectNewEventsButNotReplayHistory() {
        TestAggregate aggregate = new TestAggregate();
        TestEvent created = new TestEvent();
        aggregate.record(created);
        assertEquals(1, aggregate.pullDomainEvents().size());
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(0, aggregate.pullDomainEvents().size());
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
    @Test
    void shouldPersistThroughRegisteredRepository() {
        PersistentAggregate aggregate = new PersistentAggregate(new StringEntityId("persisted"));
        RecordingRepository repository = new RecordingRepository();
        RepositoryRegistry.register(PersistentAggregate.class, repository);
        assertEquals(aggregate, aggregate.save());
        aggregate.update();
        aggregate.delete();
        assertEquals(2, repository.saves);
        assertEquals(1, repository.deletes);
        RepositoryRegistry.unregister(PersistentAggregate.class);
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
    private static final class PersistentAggregate extends AggregateRoot<StringEntityId> {
        private final StringEntityId id; private PersistentAggregate(StringEntityId id) { this.id = id; }
        @Override public StringEntityId id() { return id; }
    }
    private static final class RecordingRepository implements Repository<PersistentAggregate, StringEntityId> {
        private int saves; private int deletes;
        @Override public Optional<PersistentAggregate> findById(StringEntityId id) { return Optional.empty(); }
        @Override public PersistentAggregate save(PersistentAggregate aggregate) { saves++; return aggregate; }
        @Override public void deleteById(StringEntityId id) { deletes++; }
    }
}
