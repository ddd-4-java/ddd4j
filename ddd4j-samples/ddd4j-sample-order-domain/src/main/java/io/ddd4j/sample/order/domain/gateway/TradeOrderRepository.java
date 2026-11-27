package io.ddd4j.sample.order.domain.gateway;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.order.domain.model.TradeOrder;

/**
 * 交易订单聚合的租户内持久化端口。
 */
public interface TradeOrderRepository extends Repository<TradeOrder, Long> {
    /**
     * 在当前租户查询指定订单。
     *
     * @param orderNo 租户内订单编号
     * @return 订单聚合；不存在时返回 {@code null}
     */
    TradeOrder findByOrderNo(String orderNo);

    /**
     * 在事务中锁定指定订单以串行化状态修改。
     *
     * @param orderNo 租户内订单编号
     * @return 已锁定的订单；不存在时返回 {@code null}
     */
    TradeOrder lockByOrderNo(String orderNo);
}
