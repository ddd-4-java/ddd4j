package io.ddd4j.ddd.repository;

import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.ddd.aggregate.DddAggregateRoot;

import java.util.List;
import java.util.Objects;

/**
 * 原生事件溯源仓储基类：子类负责聚合重建，基类负责按流读取和原子追加事件。
 *
 * @param <ID> 聚合根标识类型
 * @param <A> 聚合根类型
 */
public abstract class DddEventStoreRepository<ID extends AggregateRootId, A extends DddAggregateRoot<ID>> {
    private final EventStore eventStore;

    protected DddEventStoreRepository(EventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    protected final List<DomainEvent<?>> readEvents(String aggregateType, ID aggregateId) {
        return eventStore.read(aggregateType, aggregateId);
    }

    protected final void appendEvents(String aggregateType, ID aggregateId,
                                      List<? extends DomainEvent<?>> events, long expectedVersion) {
        eventStore.append(aggregateType, aggregateId, events, expectedVersion);
    }
}
