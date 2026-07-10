package io.ddd4j.sample.quarkus.order.infrastructure;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.order.domain.model.Order;
import io.ddd4j.sample.quarkus.order.domain.model.OrderStatus;
import io.ddd4j.sample.quarkus.order.domain.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的订单仓库实现（Quarkus 风格）。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储订单聚合根，
 * 作为 ddd4j 在 Quarkus 环境下的最简持久化示例。
 *
 * <p>真实项目应替换为 Hibernate Panache / MyBatis-Plus / JDBI 等实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryOrderRepository implements OrderRepository {

    /**
     * 内存存储：orderId -> Order 聚合根
     */
    private final ConcurrentMap<String, Order> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<Order> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        if (StrKit.isBlank(orderNo)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(order -> Objects.equals(orderNo, order.orderNo()))
                .findFirst();
    }

    @Override
    public Order save(Order aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.id(), aggregate);
        return aggregate;
    }

    @Override
    public void deleteById(String id) {
        if (StrKit.isNotBlank(id)) {
            rows.remove(id);
        }
    }

    /**
     * 当前已保存订单数量（用于测试断言与监控）。
     *
     * @return 订单数量
     */
    public long count() {
        return rows.size();
    }

    /**
     * 当前所有订单快照（按 ID 排序）。
     *
     * @return 订单列表
     */
    public List<Order> findAll() {
        return rows.values().stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    /**
     * 列举指定状态的订单。
     *
     * @param status 订单状态
     * @return 匹配的订单列表
     */
    public List<Order> findByStatus(OrderStatus status) {
        if (status == null) {
            return List.of();
        }
        return rows.values().stream()
                .filter(order -> status == order.status())
                .toList();
    }
}