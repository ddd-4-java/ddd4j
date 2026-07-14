package io.ddd4j.sample.spring.cqrs.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;

/**
 * 订单支付事件。
 *
 * <p>当订单完成支付时触发，source 为订单 ID。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderPaidEvent extends DomainEvent<StringEntityId> {

    private static final long serialVersionUID = 1L;

    public OrderPaidEvent(String orderId) {
        super(orderId);
    }
}
