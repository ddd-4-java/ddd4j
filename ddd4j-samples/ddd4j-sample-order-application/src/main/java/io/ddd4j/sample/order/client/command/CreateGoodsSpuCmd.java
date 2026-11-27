package io.ddd4j.sample.order.client.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.command.Command;
import lombok.Data;

/**
 * 上架一个指定门店可售的不限量套餐及其规格。
 */
@Data
public class CreateGoodsSpuCmd implements Command {
    /**
     * 由 HTTP 路径提供的商品所属租户。
     */
    @JsonIgnore
    private String tenantId;
    /**
     * 由 HTTP 路径提供的租户内商品编码。
     */
    @JsonIgnore
    private String spuCode;
    /**
     * 商品展示名称。
     */
    private String spuName;
    /**
     * 租户内的规格编码。
     */
    private String skuCode;
    /**
     * 商品规格的展示名称。
     */
    private String skuName;
    /**
     * 商品规格的销售价，单位为人民币分。
     */
    private Long salePriceCent;
    /**
     * 交易或商品适用的门店标识。
     */
    private Long shopId;
}
