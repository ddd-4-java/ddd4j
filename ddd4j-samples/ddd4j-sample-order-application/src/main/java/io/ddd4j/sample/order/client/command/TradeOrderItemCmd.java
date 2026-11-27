package io.ddd4j.sample.order.client.command;

import lombok.Data;

/**
 * 一条购买请求，按请求中的顺序生成稳定行号。
 */
@Data
public class TradeOrderItemCmd {
    /**
     * 租户内的规格编码。
     */
    private String skuCode;
    /**
     * 购买件数。
     */
    private Long quantity;
}
