package io.ddd4j.sample.javalin.shiro.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单行添加事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderLineAddedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderLineAddedEvent(String orderId) {
        super(orderId);
    }
}