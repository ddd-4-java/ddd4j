package io.ddd4j.sample.quarkus.satoken.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单支付事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderPaidEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderPaidEvent(String orderId) {
        super(orderId);
    }
}