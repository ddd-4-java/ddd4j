package io.ddd4j.sample.spring.satoken.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单发货事件。
 *
 * <p>当订单完成发货时触发。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderShippedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderShippedEvent(String orderId) {
        super(orderId);
    }
}
