package io.ddd4j.sample.helidon.cqrs.command;

import io.ddd4j.sample.helidon.cqrs.repository.EventSourcingOrderRepository;
import io.ddd4j.sample.order.domain.Order;


import java.util.Objects;

/**
 * 创建订单命令处理器（写侧入口）。
 *
 * <p>用 {@link Order#draft} 工厂方法创建聚合根，
 * 通过 {@link EventSourcingOrderRepository} 持久化到 EventStore。
 */

public class CreateOrderCommandHandler {

    private final EventSourcingOrderRepository orderRepository;

    public CreateOrderCommandHandler(EventSourcingOrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    public String execute(CreateOrderCommand command) {
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
        orderRepository.save(order);
        return order.id();
    }
}
