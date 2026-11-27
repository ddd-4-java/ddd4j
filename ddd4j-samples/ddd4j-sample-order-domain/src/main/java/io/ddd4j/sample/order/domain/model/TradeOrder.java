package io.ddd4j.sample.order.domain.model;

import io.ddd4j.sample.order.domain.model.TradeOrder;
import java.util.ArrayList;
import java.util.Collections;
import io.ddd4j.core.ddd.model.AggregateRoot;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.ddd4j.sample.order.domain.event.TradeOrderCancelled;
import io.ddd4j.sample.order.domain.event.TradeOrderFulfilled;

/**
 * 订单聚合，保留成交时的金额和明细。
 */
@Value
@EqualsAndHashCode(callSuper = false)
@Builder(access = AccessLevel.PRIVATE, toBuilder = true)
public class TradeOrder extends AggregateRoot<Long> {
    /**
     * 交易订单主键。
     */
    private Long id;
    /**
     * 租户标识。
     */
    private String tenantId;
    /**
     * 租户内稳定业务订单号，推送及补查共用。
     */
    private String orderNo;
    /**
     * 业务交易形态，如团购 GROUP_BUY 或预付 PREPAY；未确认时为空。
     */
    private String orderKind;
    /**
     * 约定履约方式，如凭券使用 VOUCHER、到店自提 PICKUP 或预约 RESERVATION；未确认时为空。
     */
    private String fulfillmentMode;
    /**
     * 已确认的本地下单门店ID。
     */
    private Long shopId;
    /**
     * 已确认的本地买家ID。
     */
    private Long buyerId;
    /**
     * 交易生命周期：CREATED 已创建，AWAITING_PAYMENT 待支付，CONFIRMED 已成立，CANCELLED 已取消，CLOSED 已关闭；未知为空。
     */
    private String orderState;
    /**
     * 履约结果：PENDING 待履约，PARTIAL 部分完成，FULFILLED 全部完成，EXPIRED 已过期；未知为空。
     */
    private String fulfillmentState;
    /**
     * 最近已确认的退款处理状态：NONE 无退款，PROCESSING 处理中，SUCCEEDED 成功，FAILED 失败；未知为空。
     */
    private String refundState;
    /**
     * 业务下单时间，UTC；未知为空。
     */
    private LocalDateTime placedTime;
    /**
     * 支付成功时间，UTC；未知为空。
     */
    private LocalDateTime paidTime;
    /**
     * 整单优惠前金额，人民币分；未知为空。
     */
    private Long grossAmountCent;
    /**
     * 商家承担的优惠金额，人民币分；未知为空。
     */
    private Long merchantDiscountCent;
    /**
     * 买家实际支付金额，人民币分；未知为空，不扣减后续退款。
     */
    private Long paidAmountCent;
    /**
     * 创建人ID，0表示系统。
     */
    private Long createdBy;
    /**
     * 订单记录首次写入时间。
     */
    private LocalDateTime createdTime;
    /**
     * 最近更新人ID，0表示系统。
     */
    private Long updatedBy;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedTime;
    /**
     * 逻辑删除：0 正常，1 已删除。
     */
    private Integer deleted;
    /** 订单持有的明细快照。 */
    private List<TradeOrderItem> items;

    /**
     * 按明细快照计算金额并创建待支付订单。
     *
     * @param tenant 租户编号
     * @param orderNo 租户内订单编号
     * @param shopId 门店主键
     * @param items 订单明细快照
     * @return 新建的待支付订单
     */
    public static TradeOrder place(String tenant, String orderNo, long shopId, List<TradeOrderItem> items) {
        GoodsSpu.requireText(tenant, 32);
        GoodsSpu.requireText(orderNo, 64);
        if (shopId <= 0 || Objects.isNull(items) || items.isEmpty()) {
            throw new IllegalArgumentException("订单须指定有效门店并包含明细");
        }
        long total = 0;
        for (TradeOrderItem item : items) {
            if (Objects.isNull(item) || !tenant.equals(item.getTenantId())) {
                throw new IllegalArgumentException("订单明细必须与订单属于同一租户");
            }
            total = Math.addExact(total, item.subtotalCent());
        }
        return builder().tenantId(tenant).orderNo(orderNo).shopId(shopId)
                .orderKind("GROUP_BUY").fulfillmentMode("VOUCHER").orderState("AWAITING_PAYMENT")
                .fulfillmentState("PENDING").refundState("NONE").grossAmountCent(total)
                .items(Collections.unmodifiableList(new ArrayList<>(items))).build();
    }

    /**
     * 记录已确认的支付金额和时间。
     *
     * @param amountCent 支付金额，单位为分
     * @param paidAt 支付时间
     * @return 支付确认后的订单
     */
    public TradeOrder confirmPayment(long amountCent, LocalDateTime paidAt) {
        if ("CONFIRMED".equals(orderState) && Objects.equals(paidAmountCent, amountCent)) {
            return this;
        }
        long discount = Objects.isNull(merchantDiscountCent) ? 0L : merchantDiscountCent;
        if (!"AWAITING_PAYMENT".equals(orderState) || Objects.isNull(grossAmountCent)
                || discount > grossAmountCent || amountCent != grossAmountCent - discount) {
            throw new TradeOrderStateConflict("订单状态或支付金额与待支付交易不符");
        }
        TradeOrder confirmed = toBuilder().orderState("CONFIRMED").paidAmountCent(amountCent)
                .paidTime(Objects.requireNonNull(paidAt, "支付时间")).build();
        confirmed.registerEvent(new TradeOrderPaymentConfirmed(tenantId, id, orderNo, paidAt, amountCent));
        return confirmed;
    }

    /**
     * 取消仍可取消的订单。
     *
     * @return 取消后的订单
     */
    public TradeOrder cancel() {
        if ("CANCELLED".equals(orderState)) {
            return this;
        }
        if ("FULFILLED".equals(fulfillmentState) || !("AWAITING_PAYMENT".equals(orderState)
                || "CREATED".equals(orderState) || "CONFIRMED".equals(orderState))) {
            throw new TradeOrderStateConflict("当前订单不可取消");
        }
        String refund = "CONFIRMED".equals(orderState) && Objects.nonNull(paidAmountCent) && paidAmountCent > 0
                ? "PROCESSING" : refundState;
        TradeOrder cancelled = toBuilder().orderState("CANCELLED").refundState(refund).build();
        cancelled.registerEvent(new TradeOrderCancelled(tenantId, id, orderNo));
        return cancelled;
    }

    /**
     * 登记已确认订单的履约结果。
     *
     * @return 履约后的订单
     */
    public TradeOrder fulfill() {
        if ("CONFIRMED".equals(orderState) && "FULFILLED".equals(fulfillmentState)) {
            return this;
        }
        if (!"CONFIRMED".equals(orderState) || !("PENDING".equals(fulfillmentState) || "PARTIAL".equals(fulfillmentState))) {
            throw new TradeOrderStateConflict("当前订单不可确认履约");
        }
        TradeOrder fulfilled = toBuilder().fulfillmentState("FULFILLED").build();
        fulfilled.registerEvent(new TradeOrderFulfilled(tenantId, id, orderNo));
        return fulfilled;
    }

    /**
     * 从数据库快照恢复订单，保留历史数据允许的空值。
     *
     * @param id 数据库主键
     * @param tenantId 租户编号
     * @param orderNo 租户内订单编号
     * @param orderKind 订单类型
     * @param fulfillmentMode 履约方式
     * @param shopId 门店主键
     * @param buyerId 买家编号
     * @param orderState 订单状态
     * @param fulfillmentState 履约状态
     * @param refundState 退款状态
     * @param placedTime 下单时间
     * @param paidTime 支付时间
     * @param grossAmountCent 订单总额，单位为分
     * @param merchantDiscountCent 商家优惠，单位为分
     * @param paidAmountCent 实付金额，单位为分
     * @param createdBy 创建者编号
     * @param createdTime 创建时间
     * @param updatedBy 最近更新者编号
     * @param updatedTime 最近更新时间
     * @param deleted 逻辑删除标记
     * @param items 订单明细快照
     * @return 恢复的订单聚合
     */
    public static TradeOrder restore(
            Long id, String tenantId, String orderNo,
            String orderKind, String fulfillmentMode, Long shopId,
            Long buyerId, String orderState, String fulfillmentState,
            String refundState, LocalDateTime placedTime, LocalDateTime paidTime,
            Long grossAmountCent, Long merchantDiscountCent, Long paidAmountCent,
            Long createdBy, LocalDateTime createdTime, Long updatedBy,
            LocalDateTime updatedTime, Integer deleted, List<TradeOrderItem> items) {
        if (Objects.isNull(id) || id <= 0) {
            throw new IllegalArgumentException("恢复聚合必须提供已持久化的主键");
        }
        GoodsSpu.requireText(tenantId, 32);
        GoodsSpu.requireText(orderNo, 64);
        return builder()
                .id(id).tenantId(tenantId).orderNo(orderNo)
                .orderKind(orderKind).fulfillmentMode(fulfillmentMode).shopId(shopId)
                .buyerId(buyerId).orderState(orderState).fulfillmentState(fulfillmentState)
                .refundState(refundState).placedTime(placedTime).paidTime(paidTime)
                .grossAmountCent(grossAmountCent).merchantDiscountCent(merchantDiscountCent).paidAmountCent(paidAmountCent)
                .createdBy(createdBy).createdTime(createdTime).updatedBy(updatedBy)
                .updatedTime(updatedTime).deleted(deleted).items(Collections.unmodifiableList(new ArrayList<>(items)))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public Long id() {
        return id;
    }
}
