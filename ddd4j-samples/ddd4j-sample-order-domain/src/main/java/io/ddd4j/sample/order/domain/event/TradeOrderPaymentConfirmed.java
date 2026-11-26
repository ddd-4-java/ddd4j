package io.ddd4j.sample.order.domain.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import java.time.LocalDateTime;
import lombok.Getter;

/** 四表订单的支付已确认事件。 */
@Getter
public final class TradeOrderPaymentConfirmed extends DomainEvent<StringEntityId> {
    /** 租户编号。 */
    private final String tenantId;
    /** 租户内订单编号。 */
    private final String orderNo;
    /** 支付时间。 */
    private final LocalDateTime paidTime;
    /** 实付金额，单位为分。 */
    private final long paidAmountCent;

    /**
     * 记录已确认的支付金额和支付时间。
     *
     * @param tenantId 租户编号
     * @param orderId 订单主键
     * @param orderNo 租户内订单编号
     * @param paidTime 支付时间
     * @param paidAmountCent 实付金额，单位为分
     */
    public TradeOrderPaymentConfirmed(String tenantId, Long orderId, String orderNo,
                                      LocalDateTime paidTime, long paidAmountCent) {
        super(String.valueOf(orderId));
        this.tenantId = tenantId;
        this.orderNo = orderNo;
        this.paidTime = paidTime;
        this.paidAmountCent = paidAmountCent;
    }
}
