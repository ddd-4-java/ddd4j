package io.ddd4j.sample.richmodel.order.domain.model;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.richmodel.order.domain.event.OrderCancelledEvent;
import io.ddd4j.sample.richmodel.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.richmodel.order.domain.event.OrderLineAddedEvent;
import io.ddd4j.sample.richmodel.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.richmodel.order.domain.event.OrderShippedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order aggregate root.
 *
 * <p>This model is deliberately free from MyBatis, JPA, Spring, Javalin and
 * Quarkus annotations. Persistence adapters map it to PO classes.</p>
 */
public class Order extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String orderNo;
    private final String buyerId;
    private String buyerName;
    private OrderStatus status;
    private final List<OrderLine> lines = new ArrayList<>();

    public Order(String id, String orderNo, String buyerId, String buyerName, OrderStatus status, List<OrderLine> lines) {
        if (StrKit.isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (StrKit.isBlank(orderNo)) {
            throw new IllegalArgumentException("orderNo must not be blank");
        }
        if (StrKit.isBlank(buyerId)) {
            throw new IllegalArgumentException("buyerId must not be blank");
        }
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        renameBuyer(buyerName);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (Objects.nonNull(lines)) {
            this.lines.addAll(lines);
        }
    }

    public static Order draft(String orderNo, String buyerId, String buyerName) {
        Order order = new Order(UUID.randomUUID().toString(), orderNo, buyerId, buyerName, OrderStatus.DRAFT, List.of());
        order.registerEvent(new OrderCreatedEvent(order.id()));
        return order;
    }

    @Override
    public String id() {
        return id;
    }

    public String orderNo() {
        return orderNo;
    }

    public String buyerId() {
        return buyerId;
    }

    public String buyerName() {
        return buyerName;
    }

    public OrderStatus status() {
        return status;
    }

    public List<OrderLine> lines() {
        return List.copyOf(lines);
    }

    public Money totalAmount() {
        return lines.stream()
                .map(OrderLine::subtotal)
                .reduce(Money.zero("CNY"), Money::add);
    }

    public void renameBuyer(String buyerName) {
        if (StrKit.isBlank(buyerName)) {
            throw new IllegalArgumentException("buyerName must not be blank");
        }
        this.buyerName = buyerName;
    }

    public void addLine(String productId, String productName, int quantity, Money unitPrice) {
        assertDraft();
        OrderLine line = OrderLine.create(productId, productName, quantity, unitPrice);
        lines.add(line);
        registerEvent(new OrderLineAddedEvent(id));
    }

    public void pay() {
        assertDraft();
        if (lines.isEmpty()) {
            throw new IllegalStateException("order line must not be empty");
        }
        status = OrderStatus.PAID;
        registerEvent(new OrderPaidEvent(id));
    }

    public void ship() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("only paid order can be shipped");
        }
        status = OrderStatus.SHIPPED;
        registerEvent(new OrderShippedEvent(id));
    }

    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("shipped order cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelledEvent(id));
    }

    private void assertDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("only draft order can be changed");
        }
    }
}
