package io.ddd4j.sample.javalin.order.infrastructure;

import io.ddd4j.sample.order.application.IdempotencyPort;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Javalin sample infrastructure boundary.
 *
 * <p>The in-memory implementation keeps the sample self-contained. Replacing it with
 * MyBatis-Plus, Redis and a database Outbox does not change the shared application layer.
 */
public final class JavalinOrderAdapters
        implements OrderRepository, OutboxPort, OrderReadModelPort, IdempotencyPort {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, OrderReadModel> readModels = new ConcurrentHashMap<>();
    private final Map<String, OutboxMessage> outbox = new ConcurrentHashMap<>();
    private final Map<String, Instant> idempotencyKeys = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        Order aggregate = Objects.requireNonNull(order, "order must not be null");
        orders.put(aggregate.id(), aggregate);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        return orders.values().stream()
                .filter(order -> Objects.equals(order.orderNo(), orderNo))
                .findFirst();
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
        messages.forEach(message -> outbox.put(message.id(), message));
    }

    @Override
    public List<OutboxMessage> pending(int limit) {
        return new ArrayList<>(outbox.values()).stream().limit(limit).toList();
    }

    @Override
    public void markPublished(String messageId) {
        outbox.remove(messageId);
    }

    @Override
    public void markFailed(String messageId, String reason) {
        // Keep the message pending so the publisher can retry it.
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
        OrderQuery criteria = Objects.requireNonNull(query, "query must not be null");
        return readModels.values().stream()
                .filter(order -> Objects.isNull(criteria.buyerId())
                        || Objects.equals(criteria.buyerId(), order.buyerId()))
                .filter(order -> Objects.isNull(criteria.status())
                        || Objects.equals(criteria.status(), order.status()))
                .skip((long) (criteria.page() - 1) * criteria.size())
                .limit(criteria.size())
                .toList();
    }

    @Override
    public boolean acquire(String key, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Objects.requireNonNull(ttl, "ttl must not be null"));
        AtomicBoolean acquired = new AtomicBoolean();
        idempotencyKeys.compute(key, (ignored, current) -> {
            if (Objects.isNull(current) || current.isBefore(now)) {
                acquired.set(true);
                return expiresAt;
            }
            return current;
        });
        return acquired.get();
    }

    @Override
    public void complete(String key, Object result) {
        idempotencyKeys.computeIfPresent(key, (ignored, expiresAt) -> Instant.MAX);
    }

    @Override
    public void release(String key) {
        idempotencyKeys.remove(key);
    }
}
