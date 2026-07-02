package io.ddd4j.sample.richmodel.order.domain.repository;

import io.ddd4j.core.domain.model.DomainObjectMapper;
import io.ddd4j.sample.richmodel.order.domain.model.Order;

import java.util.Optional;

/**
 * Order aggregate repository contract.
 */
public interface OrderRepository extends DomainRepository<Order, String> {

    Optional<Order> findByOrderNo(String orderNo);
}
