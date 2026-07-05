package io.ddd4j.sample.spring.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单创建事件。
 *
 * <p>当新订单被创建时触发，source 为订单 ID。
 * 通过 ddd4j {@code DomainEvent.publish()} → {@code SpringDomainEventPublisher} 桥接，
 * 最终由 Spring {@code @EventListener} 监听。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderCreatedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderCreatedEvent(String orderId) {
        super(orderId);
    }
}
