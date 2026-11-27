package io.ddd4j.sample.order.client.query;

import lombok.Value;

/**
 * 按租户和订单号查询交易快照。
 */
@Value
public class TradeOrderQuery {
    /**
     * 租户编号。
     */
    private String tenantId;
    /**
     * 租户内订单编号。
     */
    private String orderNo;
}
