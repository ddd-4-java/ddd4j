package io.ddd4j.sample.order.testkit;

import io.ddd4j.sample.order.application.IdempotencyPort;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOrderAdapters implements OrderRepository, OutboxPort, OrderReadModelPort, IdempotencyPort {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, OrderReadModel> readModels = new ConcurrentHashMap<>();
    private final Map<String, OutboxMessage> pending = new ConcurrentHashMap<>();
    private final Set<String> acquired = ConcurrentHashMap.newKeySet();

    @Override
    public void save(Order order) {
        orders.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        return orders.values().stream().filter(order -> order.orderNo().equals(orderNo)).findFirst();
    }

    @Override
    public List<Order> findAll(int offset, int limit) {
        return orders.values().stream().skip(offset).limit(limit).toList();
    }

    @Override
    public long count() {
        return orders.size();
    }

    @Override
    public void append(List<OutboxMessage> messages) {
        messages.forEach(message -> pending.put(message.id(), message));
    }

    @Override
    public List<OutboxMessage> pending(int limit) {
        return new ArrayList<>(pending.values()).stream().limit(limit).toList();
    }

    @Override
    public void markPublished(String messageId) {
        pending.remove(messageId);
    }

    @Override
    public void markFailed(String messageId, String reason) {
        // A failed message remains pending for the next retry.
    }

    @Override
    public void project(OrderReadModel order) {
        readModels.put(order.id(), order);
    }

    @Override
    public Optional<OrderReadModel> findProjectionById(String orderId) {
        return Optional.ofNullable(readModels.get(orderId));
    }

    @Override
    public List<OrderReadModel> query(OrderQuery query) {
        return readModels.values().stream()
                .filter(order -> Objects.isNull(query.buyerId()) || query.buyerId().equals(order.buyerId()))
                .filter(order -> Objects.isNull(query.status()) || query.status() == order.status())
                .skip((long) (query.page() - 1) * query.size())
                .limit(query.size())
                .toList();
    }

    @Override
    public boolean acquire(String key, Duration ttl) {
        return acquired.add(key);
    }

    @Override
    public void complete(String key, Object result) {
        acquired.add(key);
    }

    @Override
    public void release(String key) {
        acquired.remove(key);
    }
}
