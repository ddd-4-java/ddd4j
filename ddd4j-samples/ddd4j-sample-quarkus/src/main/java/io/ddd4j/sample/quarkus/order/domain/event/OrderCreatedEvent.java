package io.ddd4j.sample.quarkus.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;

/**
 * 订单创建事件。
 *
 * <p>当新订单被创建时触发，由 {@link io.ddd4j.sample.quarkus.order.domain.model.Order#draft}
 * 工厂方法或应用服务注册。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderCreatedEvent extends DomainEvent<String> {

    private static final long serialVersionUID = 1L;

    public OrderCreatedEvent(String orderId) {
        super(orderId);
    }
}