package io.ddd4j.sample.spring.security.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;

/**
 * 订单行添加事件。
 *
 * <p>当订单中添加新的商品行时触发。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderLineAddedEvent extends DomainEvent<StringEntityId> {

    private static final long serialVersionUID = 1L;

    public OrderLineAddedEvent(String orderId) {
        super(orderId);
    }
}
