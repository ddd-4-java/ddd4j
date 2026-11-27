package io.ddd4j.sample.order.client.command;

import lombok.Data;

/** 一条购买请求，按请求中的顺序生成稳定行号。 */
@Data
public class TradeOrderItemCmd {
    /** \u79df\u6237\u5185\u7684\u89c4\u683c\u7f16\u7801\u3002 */
    private String skuCode;
    /** \u8d2d\u4e70\u4ef6\u6570\u3002 */
    private Long quantity;
}
