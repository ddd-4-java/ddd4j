package io.ddd4j.sample.order.app.projection;

import io.ddd4j.sample.order.domain.model.TradeOrderItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 对外查询的不可变成交快照。
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TradeOrderItemSnapshot {
    /**
     * 持久化记录主键。
     */
    Long id;
    /**
     * 业务所属租户标识。
     */
    String tenantId;
    /**
     * 所属订单的持久化主键。
     */
    Long orderId;
    /**
     * 订单内的明细行号。
     */
    Integer lineNo;
    /**
     * 成交规格的持久化主键。
     */
    Long skuId;
    /**
     * 下单时记录的商品名称。
     */
    String productNameSnapshot;
    /**
     * 下单时记录的商品规格名称。
     */
    String skuNameSnapshot;
    /**
     * 购买件数。
     */
    Long quantity;
    /**
     * 下单时的成交单价，单位为人民币分。
     */
    Long unitPriceCent;
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
     * 复制成交明细及其审计字段，供订单投影读取。
     *
     * @param value 订单中的成交明细
     * @return 独立的成交明细快照
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
