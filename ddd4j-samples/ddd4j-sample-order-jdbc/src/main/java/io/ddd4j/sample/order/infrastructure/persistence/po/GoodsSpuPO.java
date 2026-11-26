package io.ddd4j.sample.order.infrastructure.persistence.po;

import lombok.Data;
import java.time.LocalDateTime;

/** goods_spu 持久化对象。 */
@Data
public class GoodsSpuPO {
    /** 商品主键。 */
    private Long id;
    /** 租户标识。 */
    private String tenantId;
    /** 租户内稳定商品编码，创建后不可变。 */
    private String spuCode;
    /** 对客商品名称。 */
    private String spuName;
    /** 商品形态：PACKAGE 套餐，VOUCHER 代金券，OTHER 其他已识别形态。 */
    private String productKind;
    /** 业务商品分类主键，未归类时为空。 */
    private Long categoryId;
    /** 有序商品图片地址数组，首项为主图。 */
    private String imageUrls;
    /** 适用的本地门店ID字符串数组；空数组表示无适用门店，NULL表示范围待确认，不表示全部门店。 */
    private String applicableShopIds;
    /** 本次商品资料审核进度：DRAFT 草稿，PENDING 待审，APPROVED 通过，REJECTED 驳回。 */
    private String reviewState;
    /** 最近一次业务审核意见。 */
    private String reviewNote;
    /** 是否展示给顾客：0 隐藏，1 展示；独立于可售状态。 */
    private Integer isVisible;
    /** 业务是否开放购买：0 停售，1 可售；仍需校验门店、规格及库存规则。 */
    private Integer isOnSale;
    /** 创建人ID，0表示系统。 */
    private Long createdBy;
    /** 建档时间。 */
    private LocalDateTime createdTime;
    /** 最近更新人ID，0表示系统。 */
    private Long updatedBy;
    /** 最近更新时间。 */
    private LocalDateTime updatedTime;
    /** 逻辑删除：0 正常，1 已删除。 */
    private Integer deleted;
}
