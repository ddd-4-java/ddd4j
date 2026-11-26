package io.ddd4j.sample.order.app.projection;

import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.Value;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;

import java.time.LocalDateTime;
import java.util.List;

/** 对外查询的不可变成交快照。 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TradeOrderSnapshot {
    Long id;
    String tenantId;
    String orderNo;
    String orderKind;
    String fulfillmentMode;
    Long shopId;
    Long buyerId;
    String orderState;
    String fulfillmentState;
    String refundState;
    LocalDateTime placedTime;
    LocalDateTime paidTime;
    Long grossAmountCent;
    Long merchantDiscountCent;
    Long paidAmountCent;
    Long createdBy;
    LocalDateTime createdTime;
    Long updatedBy;
    LocalDateTime updatedTime;
    Integer deleted;
    List<TradeOrderItemSnapshot> items;

    static TradeOrderSnapshot from(TradeOrder value) {
        return new TradeOrderSnapshot(
                value.getId(), value.getTenantId(), value.getOrderNo(),
                value.getOrderKind(), value.getFulfillmentMode(), value.getShopId(),
                value.getBuyerId(), value.getOrderState(), value.getFulfillmentState(),
                value.getRefundState(), value.getPlacedTime(), value.getPaidTime(),
                value.getGrossAmountCent(), value.getMerchantDiscountCent(), value.getPaidAmountCent(),
                value.getCreatedBy(), value.getCreatedTime(), value.getUpdatedBy(),
                value.getUpdatedTime(), value.getDeleted(), value.getItems().stream().map(TradeOrderItemSnapshot::from).toList());
    }
}
