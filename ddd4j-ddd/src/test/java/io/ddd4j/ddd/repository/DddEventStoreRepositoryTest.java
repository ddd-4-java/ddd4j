package io.ddd4j.ddd.repository;

import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.core.ddd.repository.EventSourcingRepository;
import io.ddd4j.ddd.aggregate.DddAggregateRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DddEventStoreRepositoryTest {
    @Test
    void shouldPersistAndRebuildAggregateFromEventStream() {
        TestId id = new TestId("order-1");
        TestRepository repository = new TestRepository();
        assertEquals(true, repository instanceof EventSourcingRepository);
        TestAggregate created = new TestAggregate(id);
        created.record("created");
        repository.add(created);
        TestAggregate restored = repository.read(id);
        assertEquals("created", restored.state);
        restored.record("paid");
        repository.update(restored);
        assertEquals("paid", repository.read(id).state);
    }
    private static final class TestRepository extends DddEventStoreRepository<TestId, TestAggregate> {
        private TestRepository() { super(new InMemoryEventStore()); }
        @Override protected TestAggregate create(TestId id) { return new TestAggregate(id); }
        @Override protected Class<TestAggregate> aggregateClass() { return TestAggregate.class; }
    }
    private static final class TestAggregate extends DddAggregateRoot<TestId> {
        private final TestId id; private String state;
        private TestAggregate(TestId id) { this.id = id; }
        @Override public TestId id() { return id; }
        private void record(String state) { apply(new StateChanged(id, state)); }
        @EventHandler private void on(StateChanged event) { state = event.state; }
    }
    private static final class StateChanged extends DomainEvent<TestId> {
        private final String state;
        private StateChanged(TestId id, String state) { super(new EntityIdPath(id)); this.state = state; }
    }
    private static final class TestId implements AggregateRootId {
        private final String value; private TestId(String value) { this.value = value; }
        @Override public EntityType getType() { return new StringEntityType("Order"); }
        @Override public String asString() { return value; }
        @Override public String asTypedString() { return "Order:" + value; }
    }
}
