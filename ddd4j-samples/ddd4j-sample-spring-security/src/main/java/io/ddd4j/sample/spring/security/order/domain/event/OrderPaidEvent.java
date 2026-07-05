package io.ddd4j.sample.spring.security.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单支付事件。
 *
 * <p>当订单完成支付时触发。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderPaidEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderPaidEvent(String orderId) {
        super(orderId);
    }
}
