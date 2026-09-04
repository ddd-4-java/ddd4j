package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.ddd.model.AggregateRoot;
import java.io.Serializable;

/** 事件溯源仓储端口：从事件流重建并追加聚合变更。 */
public interface EventSourcingRepository<M extends AggregateRoot<ID>, ID extends Serializable> {
    M read(ID aggregateId);
    M read(ID aggregateId, int version);
    void add(M aggregate);
    void update(M aggregate);
}
