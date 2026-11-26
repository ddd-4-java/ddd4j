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
    Long id;
    String tenantId;
    Long orderId;
    Integer lineNo;
    Long skuId;
    String productNameSnapshot;
    String skuNameSnapshot;
    Long quantity;
    Long unitPriceCent;
    Long createdBy;
    LocalDateTime createdTime;
    Long updatedBy;
    LocalDateTime updatedTime;
    Integer deleted;

    static TradeOrderItemSnapshot from(TradeOrderItem value) {
        return new TradeOrderItemSnapshot(
                value.getId(), value.getTenantId(), value.getOrderId(),
                value.getLineNo(), value.getSkuId(), value.getProductNameSnapshot(),
                value.getSkuNameSnapshot(), value.getQuantity(), value.getUnitPriceCent(),
                value.getCreatedBy(), value.getCreatedTime(), value.getUpdatedBy(),
                value.getUpdatedTime(), value.getDeleted());
    }
}
