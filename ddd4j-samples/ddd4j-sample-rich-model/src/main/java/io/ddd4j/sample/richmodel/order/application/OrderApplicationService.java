package io.ddd4j.sample.richmodel.order.application;

import io.ddd4j.sample.richmodel.order.domain.model.Money;
import io.ddd4j.sample.richmodel.order.domain.model.Order;
import io.ddd4j.sample.richmodel.order.domain.repository.OrderRepository;

import java.util.Objects;

/**
 * Application service: orchestration only, domain rules stay in Order.
 */
public class OrderApplicationService {

    private final OrderRepository repository;

    public OrderApplicationService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public Order createDraft(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
        repository.save(order);
        return order;
    }

    public Order addLine(AddOrderLineCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = repository.findById(command.orderId())
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + command.orderId()));
        order.addLine(command.productId(), command.productName(), command.quantity(), new Money(command.unitPrice(), "CNY"));
        repository.save(order);
        return order;
    }

    public Order pay(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.pay();
        repository.save(order);
        return order;
    }

    public Order ship(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
        order.ship();
        repository.save(order);
        return order;
    }
}
