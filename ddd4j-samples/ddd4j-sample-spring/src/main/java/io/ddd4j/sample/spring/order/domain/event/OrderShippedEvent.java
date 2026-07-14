package io.ddd4j.sample.spring.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;

/**
 * 订单发货事件。
 *
 * <p>当订单完成发货时触发，source 为订单 ID。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderShippedEvent extends DomainEvent<StringEntityId> {

    private static final long serialVersionUID = 1L;

    public OrderShippedEvent(String orderId) {
        super(orderId);
    }
}
