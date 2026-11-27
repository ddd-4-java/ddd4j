package io.ddd4j.sample.order.infrastructure.persistence.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * goods_sku 持久化对象。
 */
@Data
public class GoodsSkuPO {
    /**
     * 销售规格主键。
     */
    private Long id;
    /**
     * 租户标识。
     */
    private String tenantId;
    /**
     * 所属本地商品主键。
     */
    private Long spuId;
    /**
     * 租户内稳定规格编码，创建后不可变。
     */
    private String skuCode;
    /**
     * 可购买的规格名称。
     */
    private String skuName;
    /**
     * 当前销售单价，人民币分；NULL表示尚未定价，0表示免费。
     */
    private Long salePriceCent;
    /**
     * 展示参考价，人民币分；未设置时为空。
     */
    private Long listPriceCent;
    /**
     * 库存规则：UNSET 待配置，UNLIMITED 不限量，LIMITED 限量。
     */
    private String stockMode;
    /**
     * 该规格配置的销售总量上限；仅限量时有值，不表示实时可用库存。
     */
    private Long stockLimit;
    /**
     * 创建人ID，0表示系统。
     */
    private Long createdBy;
    /**
     * 建档时间。
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
}
