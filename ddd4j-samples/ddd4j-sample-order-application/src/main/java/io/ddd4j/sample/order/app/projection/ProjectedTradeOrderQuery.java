package io.ddd4j.sample.order.app.projection;

import lombok.Value;

/**
 * 查询已经构建的交易视图。
 */
@Value
public class ProjectedTradeOrderQuery {
    /**
     * 业务所属租户标识。
     */
    String tenantId;
    /**
     * 租户内唯一的业务订单号。
     */
    String orderNo;
}
