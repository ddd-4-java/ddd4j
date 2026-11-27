package io.ddd4j.sample.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import lombok.Getter;

/**
 * 订单已取消。
 */
@Getter
public final class TradeOrderCancelled extends DomainEvent<StringEntityId> {
    private final String tenantId;
    private final String orderNo;

    public TradeOrderCancelled(String tenantId, Long orderId, String orderNo) {
        super(String.valueOf(orderId));
        this.tenantId = tenantId;
        this.orderNo = orderNo;
    }
}
