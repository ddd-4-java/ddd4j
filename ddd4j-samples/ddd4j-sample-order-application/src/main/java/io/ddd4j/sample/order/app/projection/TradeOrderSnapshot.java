package io.ddd4j.sample.order.app.projection;

import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对外查询的不可变成交快照。
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TradeOrderSnapshot {
    /**
     * 持久化记录主键。
     */
    Long id;
    /**
     * 业务所属租户标识。
     */
    String tenantId;
    /**
     * 租户内唯一的业务订单号。
     */
    String orderNo;
    /**
     * 订单业务类型。
     */
    String orderKind;
    /**
     * 订单约定的履约方式。
     */
    String fulfillmentMode;
    /**
     * 交易或商品适用的门店标识。
     */
    Long shopId;
    /**
     * 购买方标识。
     */
    Long buyerId;
    /**
     * 订单当前的交易状态。
     */
    String orderState;
    /**
     * 订单当前的履约状态。
     */
    String fulfillmentState;
    /**
     * 订单当前的退款状态。
     */
    String refundState;
    /**
     * 下单时间，按 UTC 保存。
     */
    LocalDateTime placedTime;
    /**
     * 支付确认时间，按 UTC 保存。
     */
    LocalDateTime paidTime;
    /**
     * 优惠前的订单总额，单位为人民币分。
     */
    Long grossAmountCent;
    /**
     * 商家承担的订单优惠金额，单位为人民币分。
     */
    Long merchantDiscountCent;
    /**
     * 已确认的实付金额，单位为人民币分。
     */
    Long paidAmountCent;
    /**
     * 创建操作者标识。
     */
    Long createdBy;
    /**
     * 记录创建时间，按 UTC 保存。
     */
    LocalDateTime createdTime;
    /**
     * 最近一次更新的操作者标识。
     */
    Long updatedBy;
    /**
     * 最近一次更新时间，按 UTC 保存。
     */
    LocalDateTime updatedTime;
    /**
     * 逻辑删除标记，0 表示有效，1 表示已删除。
     */
    Integer deleted;
    /**
     * 下单时保留的商品明细快照。
     */
    List<TradeOrderItemSnapshot> items;

    /**
     * 将已提交订单及其成交明细复制到订单投影。
     *
     * @param value 已提交的订单聚合
     * @return 包含成交明细的订单快照
     */
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
