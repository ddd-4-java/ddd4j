package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.ddd.model.AggregateRoot;
import java.io.Serializable;
import java.util.Optional;

/** 持久化技术无关的领域仓储基础端口。 */
public interface Repository<M extends AggregateRoot<ID>, ID extends Serializable> {
    Optional<M> findById(ID id);
    M save(M aggregate);
    default boolean existsById(ID id) { return findById(id).isPresent(); }
    default void deleteById(ID id) { throw new UnsupportedOperationException("deleteById is not supported by this repository"); }
    default void delete(M aggregate) { if (aggregate != null) deleteById(aggregate.id()); }
}
