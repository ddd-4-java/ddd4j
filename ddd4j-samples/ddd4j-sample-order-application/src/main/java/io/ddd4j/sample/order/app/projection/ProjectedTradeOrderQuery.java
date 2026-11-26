package io.ddd4j.sample.order.app.projection;

import lombok.Value;

/** 查询已经构建的交易视图。 */
@Value
public class ProjectedTradeOrderQuery {
    String tenantId;
    String orderNo;
}
