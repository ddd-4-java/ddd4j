package io.ddd4j.sample.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.Getter;

/**
 * 订单和明细已提交的交易事件。
 */
@Getter
public final class TradeOrderPlaced extends DomainEvent<StringEntityId> {
    /** 租户编号。 */
    private final String tenantId;
    /** 租户内订单编号。 */
    private final String orderNo;
    /** 下单总额，单位为分。 */
    private final long grossAmountCent;
    /** 订单明细行数。 */
    private final int itemCount;

    /**
     * 从已提交的订单提取下单时的事件载荷。
     *
     * @param order 已提交的订单聚合
     */
    public TradeOrderPlaced(TradeOrder order) {
        super(String.valueOf(order.id()));
        tenantId = order.getTenantId();
        orderNo = order.getOrderNo();
        grossAmountCent = order.getGrossAmountCent();
        itemCount = order.getItems().size();
    }
}
