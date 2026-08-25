package io.ddd4j.sample.micronaut.cqrs.readmodel;

import io.ddd4j.sample.micronaut.cqrs.cqrs.ProjectionView;
import io.ddd4j.sample.micronaut.cqrs.repository.EventSourcingOrderRepository;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.order.domain.event.OrderPaidEvent;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单摘要投影视图（CQRS 读侧，Micronaut 运行时）。
 *
 * <p>订阅 {@link OrderCreatedEvent} 和 {@link OrderPaidEvent}，
 * 从 EventSourcingOrderRepository 获取订单详情，维护内存读模型。
 */
@Singleton
public class OrderSummaryView implements ProjectionView {

    private static final String NAME = "order-summary-view";

    private final Map<String, OrderSummaryViewEntity> store = new ConcurrentHashMap<>();
    private final EventSourcingOrderRepository orderRepository;

    public OrderSummaryView(EventSourcingOrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void handleEvents(List<Object> events) {
        for (Object event : events) {
            if (event instanceof OrderCreatedEvent created) {
                handleCreated(created);
            } else if (event instanceof OrderPaidEvent paid) {
                handlePaid(paid);
            }
        }
    }

    private void handleCreated(OrderCreatedEvent event) {
        String orderId = event.source();
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isPresent()) {
            Order o = order.get();
            OrderSummaryViewEntity entity = new OrderSummaryViewEntity(
                    orderId,
                    o.orderNo(),
                    o.buyerId(),
                    o.buyerName(),
                    "DRAFT"
            );
            store.put(orderId, entity);
        }
    }

    private void handlePaid(OrderPaidEvent event) {
        String orderId = event.source();
        OrderSummaryViewEntity entity = store.get(orderId);
        if (entity != null) {
            entity.setStatus("PAID");
        }
    }

    public OrderSummaryViewEntity findById(String orderId) {
        return store.get(orderId);
    }

    public void clear() {
        store.clear();
    }
}
