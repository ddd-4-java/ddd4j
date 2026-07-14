package io.ddd4j.sample.order.domain;

public interface OrderPersistenceMapper<P> {
    P toPersistence(Order order);
    Order toDomain(P persistenceObject);
}
