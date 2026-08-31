package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import java.util.List;

/** 事件溯源存储的最小同步契约。 */
public interface EventStore {
    void append(String aggregateType, AggregateRootId aggregateId, List<? extends DomainEvent<?>> events, long expectedVersion);
    List<DomainEvent<?>> read(String aggregateType, AggregateRootId aggregateId);
}
