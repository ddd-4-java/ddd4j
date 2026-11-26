package io.ddd4j.sample.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import java.time.LocalDateTime;
import lombok.Getter;

/** 四表订单的支付已确认事件。 */
@Getter
public final class TradeOrderPaymentConfirmed extends DomainEvent<StringEntityId> {
    private final String tenantId;
    private final String orderNo;
    private final LocalDateTime paidTime;
    private final long paidAmountCent;

    public TradeOrderPaymentConfirmed(String tenantId, Long orderId, String orderNo,
                                      LocalDateTime paidTime, long paidAmountCent) {
        super(String.valueOf(orderId));
        this.tenantId = tenantId;
        this.orderNo = orderNo;
        this.paidTime = paidTime;
        this.paidAmountCent = paidAmountCent;
    }
}
