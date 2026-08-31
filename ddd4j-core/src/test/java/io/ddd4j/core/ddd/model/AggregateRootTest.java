package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        void record(TestEvent event) { apply(event); }
    }
    private static final class TestEvent extends DomainEvent<StringEntityId> {
        TestEvent() { super(new EntityIdPath(new StringEntityId("order-1"))); }
    }
}
