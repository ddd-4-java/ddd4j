package io.ddd4j.ddd.repository;

import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.repository.EventSourcingRepository;
import io.ddd4j.ddd.aggregate.DddAggregateRoot;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 原生事件溯源仓储基类：子类负责聚合重建，基类负责按流读取和原子追加事件。
 *
 * @param <ID> 聚合根标识类型
 * @param <A> 聚合根类型
 */
public abstract class DddEventStoreRepository<ID extends AggregateRootId, A extends DddAggregateRoot<ID>>
        implements EventSourcingRepository<A, ID> {
    private final EventStore eventStore;

    protected DddEventStoreRepository(EventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    protected final List<DomainEvent<?>> readEvents(String aggregateType, ID aggregateId) {
        List<StoredEvent> storedEvents = eventStore.read(aggregateType, aggregateId);
        List<DomainEvent<?>> events = new ArrayList<DomainEvent<?>>();
        for (StoredEvent storedEvent : storedEvents) {
            events.add(storedEvent.payload());
        }
        return events;
    }

    protected final void appendEvents(String aggregateType, ID aggregateId,
                                      List<? extends DomainEvent<?>> events, long expectedVersion) {
        eventStore.append(aggregateType, aggregateId, events, expectedVersion);
    }

    /** 从完整事件流重建聚合。 */
    public A read(ID aggregateId) {
        A aggregate = create(aggregateId);
        aggregate.loadFromHistory(readEvents(aggregateType(), aggregateId));
        return aggregate;
    }

    /** 从指定历史版本重建聚合。 */
    public A read(ID aggregateId, int version) {
        A aggregate = create(aggregateId);
        List<StoredEvent> storedEvents = eventStore.read(aggregateType(), aggregateId, 1L, version);
        List<DomainEvent<?>> events = new ArrayList<DomainEvent<?>>();
        for (StoredEvent storedEvent : storedEvents) {
            events.add(storedEvent.payload());
        }
        aggregate.loadFromHistory(events);
        return aggregate;
    }

    /** 新建聚合的事件流必须从版本 0 追加。 */
    public void add(A aggregate) {
        append(aggregate, 0L);
    }

    /** 在当前流版本之后追加聚合未提交事件。 */
    public void update(A aggregate) {
        long actualVersion = eventStore.read(aggregateType(), aggregate.id()).size();
        append(aggregate, actualVersion);
    }

    private void append(A aggregate, long expectedVersion) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        List<DomainEvent<?>> changes = aggregate.getUncommittedChanges();
        if (changes.isEmpty()) {
            return;
        }
        eventStore.append(aggregateType(), aggregate.id(), changes, expectedVersion);
        aggregate.clearUncommittedChanges();
    }

    /** 子类提供回放用的空/初始聚合实例。 */
    protected abstract A create(ID aggregateId);

    /** 子类提供对应聚合类型，默认使用聚合类的稳定限定名。 */
    protected String aggregateType() {
        return aggregateClass().getName();
    }

    protected abstract Class<A> aggregateClass();
}
