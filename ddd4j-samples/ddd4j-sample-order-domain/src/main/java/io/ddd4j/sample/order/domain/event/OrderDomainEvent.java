package io.ddd4j.sample.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;

public abstract class OrderDomainEvent extends DomainEvent<StringEntityId> {

    private static final long serialVersionUID = 1L;

    protected OrderDomainEvent(String orderId) {
        super(orderId);
    }
}
