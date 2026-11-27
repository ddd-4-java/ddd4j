package io.ddd4j.sample.order.domain.model;

import io.ddd4j.core.ddd.model.Entity;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 商品的当前销售规格与价格。
 */
@Value
@Builder
public class GoodsSku implements Entity<Long> {

    /**
     * 销售规格主键。
     */
    Long id;
    /**
     * 租户标识。
     */
    String tenantId;
    /**
     * 所属本地商品主键。
     */
    Long spuId;
    /**
     * 租户内稳定规格编码，创建后不可变。
     */
    String skuCode;
    /**
     * 可购买的规格名称。
     */
    String skuName;
    /**
     * 当前销售单价，人民币分；NULL表示尚未定价，0表示免费。
     */
    Long salePriceCent;
    /**
     * 展示参考价，人民币分；未设置时为空。
     */
    Long listPriceCent;
    /**
     * 库存规则：UNSET 待配置，UNLIMITED 不限量，LIMITED 限量。
     */
    String stockMode;
    /**
     * 该规格配置的销售总量上限；仅限量时有值，不表示实时可用库存。
     */
    Long stockLimit;
    /**
     * 创建人ID，0表示系统。
     */
    Long createdBy;
    /**
     * 建档时间。
     */
    LocalDateTime createdTime;
    /**
     * 最近更新人ID，0表示系统。
     */
    Long updatedBy;
    /**
     * 最近更新时间。
     */
    LocalDateTime updatedTime;
    /**
     * 逻辑删除：0 正常，1 已删除。
     */
    Integer deleted;

    /**
     * 创建不限库存的可售商品规格。
     *
     * @param tenant 租户编号
     * @param code 业务编号
     * @param name 业务名称
     * @param priceCent 销售单价，单位为分
     * @return 新的规格实体
     */
    public static GoodsSku unlimited(String tenant, String code, String name, Long priceCent) {
        GoodsSpu.requireText(code, 64);
        GoodsSpu.requireText(name, 256);
        if (Objects.isNull(priceCent) || priceCent < 0) {
            throw new IllegalArgumentException("规格价格必须为非负分值");
        }
        return builder().tenantId(tenant).skuCode(code).skuName(name)
                .salePriceCent(priceCent).stockMode("UNLIMITED").build();
    }

    /** {@inheritDoc} */
    @Override
    public Long id() {
        return id;
    }
}
