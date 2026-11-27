package io.ddd4j.sample.order.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 商品聚合，包含当前销售规格。
 */
@Value
@EqualsAndHashCode(callSuper = false)
@Builder(access = AccessLevel.PRIVATE)
public class GoodsSpu extends AggregateRoot<Long> {

    /**
     * 商品主键。
     */
    Long id;
    /**
     * 租户标识。
     */
    String tenantId;
    /**
     * 租户内稳定商品编码，创建后不可变。
     */
    String spuCode;
    /**
     * 对客商品名称。
     */
    String spuName;
    /**
     * 商品形态：PACKAGE 套餐，VOUCHER 代金券，OTHER 其他已识别形态。
     */
    String productKind;
    /**
     * 业务商品分类主键，未归类时为空。
     */
    Long categoryId;
    /**
     * 有序商品图片地址数组，首项为主图。
     */
    List<String> imageUrls;
    /**
     * 适用的本地门店ID字符串数组；空数组表示无适用门店，NULL表示范围待确认，不表示全部门店。
     */
    List<String> applicableShopIds;
    /**
     * 本次商品资料审核进度：DRAFT 草稿，PENDING 待审，APPROVED 通过，REJECTED 驳回。
     */
    String reviewState;
    /**
     * 最近一次业务审核意见。
     */
    String reviewNote;
    /**
     * 是否展示给顾客：0 隐藏，1 展示；独立于可售状态。
     */
    Integer isVisible;
    /**
     * 业务是否开放购买：0 停售，1 可售；仍需校验门店、规格及库存规则。
     */
    Integer isOnSale;
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
     * 商品销售规格列表。
     */
    List<GoodsSku> skus;

    /**
     * 创建指定门店的套餐商品并记录初始规格。
     *
     * @param tenant 租户编号
     * @param code 业务编号
     * @param name 业务名称
     * @param shopId 门店主键
     * @param sku 初始商品规格
     * @return 新的商品聚合
     */
    public static GoodsSpu listPackage(String tenant, String code, String name, long shopId, GoodsSku sku) {
        requireText(tenant, 32);
        requireText(code, 64);
        requireText(name, 256);
        if (shopId <= 0) {
            throw new IllegalArgumentException("商品须绑定有效门店");
        }
        if (Objects.isNull(sku) || !tenant.equals(sku.getTenantId())) {
            throw new IllegalArgumentException("商品与规格必须属于同一租户");
        }
        return builder().tenantId(tenant).spuCode(code).spuName(name)
                .productKind("PACKAGE").reviewState("APPROVED").isVisible(1).isOnSale(1)
                .applicableShopIds(Collections.singletonList(Long.toString(shopId))).skus(Collections.singletonList(sku)).build();
    }

    /**
     * \u8981\u6c42\u6587\u672c\u975e\u7a7a\u767d\u3001\u957f\u5ea6\u4e0d\u8d85\u8fc7\u9650\u5236\u4e14\u65e0\u9996\u5c3e\u7a7a\u767d\u3002
     * @param value \u5f85\u6821\u9a8c\u6587\u672c
     * @param max \u5b57\u7b26\u4e32\u957f\u5ea6\u4e0a\u9650
     * @throws IllegalArgumentException \u6587\u672c\u4e3a\u7a7a\u767d\u3001\u8fc7\u957f\u6216\u542b\u9996\u5c3e\u7a7a\u767d
     */
    static void requireText(String value, int max) {
        if (StrKit.isBlank(value) || value.length() > max || !value.equals(StrKit.trimWhitespace(value))) {
            throw new IllegalArgumentException("名称或编码为空、超长或含首尾空白");
        }
    }

    /**
     * 从数据库快照恢复商品，保留历史数据允许的空值。
     *
     * @param id 数据库主键
     * @param tenantId 租户编号
     * @param spuCode 租户内 SPU 编号
     * @param spuName 商品名称
     * @param productKind 商品类型
     * @param categoryId 类目编号
     * @param imageUrls 商品图片地址
     * @param applicableShopIds 适用门店编号列表
     * @param reviewState 审核状态
     * @param reviewNote 审核备注
     * @param isVisible 可见标记
     * @param isOnSale 上架标记
     * @param createdBy 创建者编号
     * @param createdTime 创建时间
     * @param updatedBy 最近更新者编号
     * @param updatedTime 最近更新时间
     * @param deleted 逻辑删除标记
     * @param skus 规格集合
     * @return 恢复的商品聚合
     */
    public static GoodsSpu restore(
            Long id, String tenantId, String spuCode,
            String spuName, String productKind, Long categoryId,
            List<String> imageUrls, List<String> applicableShopIds, String reviewState,
            String reviewNote, Integer isVisible, Integer isOnSale,
            Long createdBy, LocalDateTime createdTime, Long updatedBy,
            LocalDateTime updatedTime, Integer deleted, List<GoodsSku> skus) {
        if (Objects.isNull(id) || id <= 0) {
            throw new IllegalArgumentException("恢复聚合必须提供已持久化的主键");
        }
        requireText(tenantId, 32);
        requireText(spuCode, 64);
        return builder()
                .id(id).tenantId(tenantId).spuCode(spuCode)
                .spuName(spuName).productKind(productKind).categoryId(categoryId)
                .imageUrls(Objects.isNull(imageUrls) ? null : Collections.unmodifiableList(new ArrayList<>(imageUrls))).applicableShopIds(Objects.isNull(applicableShopIds) ? null : Collections.unmodifiableList(new ArrayList<>(applicableShopIds))).reviewState(reviewState)
                .reviewNote(reviewNote).isVisible(isVisible).isOnSale(isOnSale)
                .createdBy(createdBy).createdTime(createdTime).updatedBy(updatedBy)
                .updatedTime(updatedTime).deleted(deleted).skus(Collections.unmodifiableList(new ArrayList<>(skus)))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public Long id() {
        return id;
    }

}
