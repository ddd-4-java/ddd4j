package io.ddd4j.sample.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import lombok.Getter;

/**
 * 订单已履约。
 */
@Getter
public final class TradeOrderFulfilled extends DomainEvent<StringEntityId> {
    /** 租户编号。 */
    private final String tenantId;
    /** 租户内订单编号。 */
    private final String orderNo;

    /**
     * 记录订单履约后的事件。
     *
     * @param tenantId 租户编号
     * @param orderId 订单主键
     * @param orderNo 租户内订单编号
     */
    public TradeOrderFulfilled(String tenantId, Long orderId, String orderNo) {
        super(String.valueOf(orderId));
        this.tenantId = tenantId;
        this.orderNo = orderNo;
    }
}
