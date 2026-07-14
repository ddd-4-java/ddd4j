package io.ddd4j.sample.order.domain;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(String orderId);
    Optional<Order> findByOrderNo(String orderNo);
    List<Order> findAll(int offset, int limit);
    long count();
}
