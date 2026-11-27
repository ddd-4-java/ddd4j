package io.ddd4j.sample.order.app.projection;

import java.util.Collections;
import java.util.stream.Collectors;
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
    /** \u6301\u4e45\u5316\u8bb0\u5f55\u4e3b\u952e\u3002 */
    Long id;
    /** \u4e1a\u52a1\u6240\u5c5e\u79df\u6237\u6807\u8bc6\u3002 */
    String tenantId;
    /** \u79df\u6237\u5185\u552f\u4e00\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7\u3002 */
    String orderNo;
    /** \u8ba2\u5355\u4e1a\u52a1\u7c7b\u578b\u3002 */
    String orderKind;
    /** \u8ba2\u5355\u7ea6\u5b9a\u7684\u5c65\u7ea6\u65b9\u5f0f\u3002 */
    String fulfillmentMode;
    /** \u4ea4\u6613\u6216\u5546\u54c1\u9002\u7528\u7684\u95e8\u5e97\u6807\u8bc6\u3002 */
    Long shopId;
    /** \u8d2d\u4e70\u65b9\u6807\u8bc6\u3002 */
    Long buyerId;
    /** \u8ba2\u5355\u5f53\u524d\u7684\u4ea4\u6613\u72b6\u6001\u3002 */
    String orderState;
    /** \u8ba2\u5355\u5f53\u524d\u7684\u5c65\u7ea6\u72b6\u6001\u3002 */
    String fulfillmentState;
    /** \u8ba2\u5355\u5f53\u524d\u7684\u9000\u6b3e\u72b6\u6001\u3002 */
    String refundState;
    /** \u4e0b\u5355\u65f6\u95f4\uff0c\u6309 UTC \u4fdd\u5b58\u3002 */
    LocalDateTime placedTime;
    /** \u652f\u4ed8\u786e\u8ba4\u65f6\u95f4\uff0c\u6309 UTC \u4fdd\u5b58\u3002 */
    LocalDateTime paidTime;
    /** \u4f18\u60e0\u524d\u7684\u8ba2\u5355\u603b\u989d\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206\u3002 */
    Long grossAmountCent;
    /** \u5546\u5bb6\u627f\u62c5\u7684\u8ba2\u5355\u4f18\u60e0\u91d1\u989d\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206\u3002 */
    Long merchantDiscountCent;
    /** \u5df2\u786e\u8ba4\u7684\u5b9e\u4ed8\u91d1\u989d\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206\u3002 */
    Long paidAmountCent;
    /** \u521b\u5efa\u64cd\u4f5c\u8005\u6807\u8bc6\u3002 */
    Long createdBy;
    /** \u8bb0\u5f55\u521b\u5efa\u65f6\u95f4\uff0c\u6309 UTC \u4fdd\u5b58\u3002 */
    LocalDateTime createdTime;
    /** \u6700\u8fd1\u4e00\u6b21\u66f4\u65b0\u7684\u64cd\u4f5c\u8005\u6807\u8bc6\u3002 */
    Long updatedBy;
    /** \u6700\u8fd1\u4e00\u6b21\u66f4\u65b0\u65f6\u95f4\uff0c\u6309 UTC \u4fdd\u5b58\u3002 */
    LocalDateTime updatedTime;
    /** \u903b\u8f91\u5220\u9664\u6807\u8bb0\uff0c0 \u8868\u793a\u6709\u6548\uff0c1 \u8868\u793a\u5df2\u5220\u9664\u3002 */
    Integer deleted;
    /** \u4e0b\u5355\u65f6\u4fdd\u7559\u7684\u5546\u54c1\u660e\u7ec6\u5feb\u7167\u3002 */
    List<TradeOrderItemSnapshot> items;

    /**
     * \u5c06\u5df2\u63d0\u4ea4\u8ba2\u5355\u53ca\u5176\u6210\u4ea4\u660e\u7ec6\u590d\u5236\u5230\u8ba2\u5355\u6295\u5f71\u3002
     * @param value \u5df2\u63d0\u4ea4\u7684\u8ba2\u5355\u805a\u5408
     * @return \u5305\u542b\u6210\u4ea4\u660e\u7ec6\u7684\u8ba2\u5355\u5feb\u7167
     */
    static TradeOrderSnapshot from(TradeOrder value) {
        return new TradeOrderSnapshot(
                value.getId(), value.getTenantId(), value.getOrderNo(),
                value.getOrderKind(), value.getFulfillmentMode(), value.getShopId(),
                value.getBuyerId(), value.getOrderState(), value.getFulfillmentState(),
                value.getRefundState(), value.getPlacedTime(), value.getPaidTime(),
                value.getGrossAmountCent(), value.getMerchantDiscountCent(), value.getPaidAmountCent(),
                value.getCreatedBy(), value.getCreatedTime(), value.getUpdatedBy(),
                value.getUpdatedTime(), value.getDeleted(), Collections.unmodifiableList(
                        value.getItems().stream().map(TradeOrderItemSnapshot::from).collect(Collectors.toList())));
    }
}
