package io.ddd4j.sample.order.local;

import java.util.stream.Collectors;
import io.ddd4j.sample.order.application.IdempotencyPort;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OrderTransactionPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单进程订单适配器，用于本地开发、Web 示例与无外部中间件的契约验证。
 *
 * <p>它实现所有订单端口，但不作为生产持久化方案；生产运行时应改用 JDBC、Redis 与 Kafka 适配器。
 */
public final class InMemoryOrderAdapters implements OrderRepository, OutboxPort, OrderReadModelPort, IdempotencyPort,
        OrderTransactionPort {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, OrderReadModel> readModels = new ConcurrentHashMap<>();
    private final Map<String, OutboxMessage> pending = new ConcurrentHashMap<>();
    private final Set<String> acquired = ConcurrentHashMap.newKeySet();
    private final Object transactionMonitor = new Object();

    @Override
    public void execute(Runnable operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        synchronized (transactionMonitor) {
            operation.run();
        }
    }

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
        return orders.values().stream().skip(offset).limit(limit).collect(java.util.stream.Collectors.toList());
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
        return new ArrayList<>(pending.values()).stream().limit(limit).collect(java.util.stream.Collectors.toList());
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
        OrderQuery criteria = Objects.requireNonNull(query, "query must not be null");
        return readModels.values().stream()
                .filter(order -> Objects.isNull(criteria.buyerId()) || criteria.buyerId().equals(order.buyerId()))
                .filter(order -> Objects.isNull(criteria.status()) || criteria.status() == order.status())
                .skip((long) (criteria.page() - 1) * criteria.size())
                .limit(criteria.size())
                .collect(java.util.stream.Collectors.toList());
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
