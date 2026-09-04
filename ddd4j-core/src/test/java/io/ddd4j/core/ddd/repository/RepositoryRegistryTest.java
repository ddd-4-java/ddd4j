package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryRegistryTest {
    @Test
    void shouldRegisterResolveAndUnregisterRepository() {
        InMemoryRepository repository = new InMemoryRepository();
        RepositoryRegistry.register(TestAggregate.class, repository);
        assertSame(repository, RepositoryRegistry.repository(TestAggregate.class));
        TestAggregate aggregate = new TestAggregate(new StringEntityId("a-1"));
        repository.save(aggregate);
        assertEquals(aggregate, RepositoryRegistry.repository(TestAggregate.class).findById(aggregate.id()).get());
        RepositoryRegistry.unregister(TestAggregate.class);
        assertThrows(IllegalStateException.class, () -> RepositoryRegistry.repository(TestAggregate.class));
    }
    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        private final StringEntityId id; private TestAggregate(StringEntityId id) { this.id = id; }
        @Override public StringEntityId id() { return id; }
    }
    private static final class InMemoryRepository implements Repository<TestAggregate, StringEntityId> {
        private final Map<StringEntityId, TestAggregate> values = new HashMap<StringEntityId, TestAggregate>();
        @Override public Optional<TestAggregate> findById(StringEntityId id) { return Optional.ofNullable(values.get(id)); }
        @Override public TestAggregate save(TestAggregate aggregate) { values.put(aggregate.id(), aggregate); return aggregate; }
    }
}
