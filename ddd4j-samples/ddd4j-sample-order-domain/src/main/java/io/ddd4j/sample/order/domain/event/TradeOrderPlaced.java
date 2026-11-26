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
    private final String tenantId;
    private final String orderNo;
    private final long grossAmountCent;
    private final int itemCount;

    public TradeOrderPlaced(TradeOrder order) {
        super(String.valueOf(order.id()));
        tenantId = order.getTenantId();
        orderNo = order.getOrderNo();
        grossAmountCent = order.getGrossAmountCent();
        itemCount = order.getItems().size();
    }
}
