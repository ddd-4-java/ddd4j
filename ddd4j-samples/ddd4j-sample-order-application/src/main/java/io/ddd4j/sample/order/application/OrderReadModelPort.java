package io.ddd4j.sample.order.application;

import io.ddd4j.sample.order.domain.OrderQuery;

import java.util.List;
import java.util.Optional;

public interface OrderReadModelPort {
    void project(OrderReadModel order);
    Optional<OrderReadModel> findProjectionById(String orderId);
    List<OrderReadModel> query(OrderQuery query);
}
