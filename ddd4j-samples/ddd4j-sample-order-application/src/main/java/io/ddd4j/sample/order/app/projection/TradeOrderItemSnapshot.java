package io.ddd4j.sample.order.app.projection;

import io.ddd4j.sample.order.domain.model.TradeOrderItem;
import lombok.Value;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;

import java.time.LocalDateTime;

/** 对外查询的不可变成交快照。 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TradeOrderItemSnapshot {
    /** \u6301\u4e45\u5316\u8bb0\u5f55\u4e3b\u952e\u3002 */
    Long id;
    /** \u4e1a\u52a1\u6240\u5c5e\u79df\u6237\u6807\u8bc6\u3002 */
    String tenantId;
    /** \u6240\u5c5e\u8ba2\u5355\u7684\u6301\u4e45\u5316\u4e3b\u952e\u3002 */
    Long orderId;
    /** \u8ba2\u5355\u5185\u7684\u660e\u7ec6\u884c\u53f7\u3002 */
    Integer lineNo;
    /** \u6210\u4ea4\u89c4\u683c\u7684\u6301\u4e45\u5316\u4e3b\u952e\u3002 */
    Long skuId;
    /** \u4e0b\u5355\u65f6\u8bb0\u5f55\u7684\u5546\u54c1\u540d\u79f0\u3002 */
    String productNameSnapshot;
    /** \u4e0b\u5355\u65f6\u8bb0\u5f55\u7684\u5546\u54c1\u89c4\u683c\u540d\u79f0\u3002 */
    String skuNameSnapshot;
    /** \u8d2d\u4e70\u4ef6\u6570\u3002 */
    Long quantity;
    /** \u4e0b\u5355\u65f6\u7684\u6210\u4ea4\u5355\u4ef7\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206\u3002 */
    Long unitPriceCent;
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

    /**
     * \u590d\u5236\u6210\u4ea4\u660e\u7ec6\u53ca\u5176\u5ba1\u8ba1\u5b57\u6bb5\uff0c\u4f9b\u8ba2\u5355\u6295\u5f71\u8bfb\u53d6\u3002
     * @param value \u8ba2\u5355\u4e2d\u7684\u6210\u4ea4\u660e\u7ec6
     * @return \u72ec\u7acb\u7684\u6210\u4ea4\u660e\u7ec6\u5feb\u7167
     */
    static TradeOrderItemSnapshot from(TradeOrderItem value) {
        return new TradeOrderItemSnapshot(
                value.getId(), value.getTenantId(), value.getOrderId(),
                value.getLineNo(), value.getSkuId(), value.getProductNameSnapshot(),
                value.getSkuNameSnapshot(), value.getQuantity(), value.getUnitPriceCent(),
                value.getCreatedBy(), value.getCreatedTime(), value.getUpdatedBy(),
                value.getUpdatedTime(), value.getDeleted());
    }
}
