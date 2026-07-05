package io.ddd4j.sample.spring.cqrs.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单行添加事件。
 *
 * <p>当订单中添加新的商品行时触发，source 为订单 ID。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderLineAddedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderLineAddedEvent(String orderId) {
        super(orderId);
    }
}
