package io.ddd4j.sample.javalin.shiro.order.infrastructure;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Money;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Order;
import io.ddd4j.sample.javalin.shiro.order.domain.model.OrderLine;
import io.ddd4j.sample.javalin.shiro.order.domain.model.OrderStatus;
import io.ddd4j.sample.javalin.shiro.order.domain.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的订单仓储实现（演示用）。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储订单聚合的内部字段映射。
 * Quarkus 下用 {@link ApplicationScoped} 取代 Spring 的 {@code @Repository}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryOrderRepository implements OrderRepository {

    private final ConcurrentMap<String, OrderRow> rows = new ConcurrentHashMap<>();

    private static OrderRow toRow(Order order) {
        OrderRow row = new OrderRow();
        row.id = order.id();
        row.orderNo = order.orderNo();
        row.buyerId = order.buyerId();
        row.buyerName = order.buyerName();
        row.status = order.status().name();
        row.lines = new ArrayList<>();
        for (OrderLine line : order.lines()) {
            OrderLineRow lr = new OrderLineRow();
            lr.id = line.id();
            lr.goodsId = line.goodsId();
            lr.goodsName = line.goodsName();
            lr.quantity = line.quantity();
            lr.unitPrice = line.unitPrice().amount();
            lr.currency = line.unitPrice().currency();
            row.lines.add(lr);
        }
        return row;
    }

    private static Order toModel(OrderRow row) {
        List<OrderLine> lines = Optional.ofNullable(row.lines).orElseGet(List::of).stream()
                .map(lr -> new OrderLine(lr.id, lr.goodsId, lr.goodsName, lr.quantity,
                        new Money(lr.unitPrice, lr.currency)))
                .toList();
        return new Order(row.id, row.orderNo, row.buyerId, row.buyerName,
                OrderStatus.valueOf(row.status), lines);
    }

    @Override
    public Optional<Order> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryOrderRepository::toModel);
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        if (StrKit.isBlank(orderNo)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(row -> Objects.equals(orderNo, row.orderNo))
                .findFirst()
                .map(InMemoryOrderRepository::toModel);
    }

    @Override
    public List<Order> findAll() {
        return rows.values().stream()
                .map(InMemoryOrderRepository::toModel)
                .toList();
    }

    // ============================ 模型与行转换 ============================

    @Override
    public Order save(Order aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.id(), toRow(aggregate));
        return aggregate;
    }

    @Override
    public void deleteById(String id) {
        if (StrKit.isNotBlank(id)) {
            rows.remove(id);
        }
    }

    /**
     * 订单内部持久化行。
     */
    static class OrderRow {

        String id;
        String orderNo;
        String buyerId;
        String buyerName;
        String status;
        List<OrderLineRow> lines;
    }

    /**
     * 订单行内部持久化行。
     */
    static class OrderLineRow {

        String id;
        String goodsId;
        String goodsName;
        Integer quantity;
        BigDecimal unitPrice;
        String currency;
    }
}