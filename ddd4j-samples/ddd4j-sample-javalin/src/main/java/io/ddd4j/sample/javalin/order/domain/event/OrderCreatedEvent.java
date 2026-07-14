package io.ddd4j.sample.javalin.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;

/**
 * 订单创建事件。
 *
 * <p>当新订单被创建（{@code Order.draft(...)}）时触发。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderCreatedEvent extends DomainEvent<StringEntityId> {

    private static final long serialVersionUID = 1L;

    public OrderCreatedEvent(String orderId) {
        super(orderId);
    }
}
