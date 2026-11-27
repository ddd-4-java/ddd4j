package io.ddd4j.sample.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import lombok.Getter;

/**
 * 订单已履约。
 */
@Getter
public final class TradeOrderFulfilled extends DomainEvent<StringEntityId> {
    private final String tenantId;
    private final String orderNo;

    public TradeOrderFulfilled(String tenantId, Long orderId, String orderNo) {
        super(String.valueOf(orderId));
        this.tenantId = tenantId;
        this.orderNo = orderNo;
    }
}
