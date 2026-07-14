package io.ddd4j.sample.quarkus.satoken.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;

/**
 * 订单行添加事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderLineAddedEvent extends DomainEvent<StringEntityId> {

    private static final long serialVersionUID = 1L;

    public OrderLineAddedEvent(String orderId) {
        super(orderId);
    }
}