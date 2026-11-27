package io.ddd4j.sample.order.client.query;

import lombok.Value;

/**
 * 按租户和商品编码查询商品。
 */
@Value
public class GoodsSpuQuery {
    /**
     * 租户编号。
     */
    private String tenantId;
    /**
     * 租户内商品编号。
     */
    private String spuCode;
}
