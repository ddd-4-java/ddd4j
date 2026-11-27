package io.ddd4j.sample.order.client.query;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * 按租户和订单号查询交易快照。
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class TradeOrderQuery extends Query<TradeOrder> {
    /**
     * 租户编号。
     */
    private String tenantId;
    /**
     * 租户内订单编号。
     */
    private String orderNo;
}
