package io.ddd4j.sample.javalin.shiro.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;

/**
 * 订单支付事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderPaidEvent extends DomainEvent<StringEntityId> {

    private static final long serialVersionUID = 1L;

    public OrderPaidEvent(String orderId) {
        super(orderId);
    }
}