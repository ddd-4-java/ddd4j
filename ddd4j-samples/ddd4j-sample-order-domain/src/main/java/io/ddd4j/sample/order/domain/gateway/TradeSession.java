package io.ddd4j.sample.order.domain.gateway;

import java.util.function.Function;

/**
 * 一次业务调用内共用同一个数据库事务。
 */
public interface TradeSession {
    /**
     * 取得当前事务中的商品仓储。
     *
     * @return 商品仓储
     */
    GoodsSpuRepository goods();

    /**
     * 取得当前事务中的订单仓储。
     *
     * @return 订单仓储
     */
    TradeOrderRepository orders();

    /**
     * 以租户为边界组织商品及交易操作的事务端口。
     */
    interface Transactions {
        /**
         * 在指定租户的事务中执行操作。
         *
         * @param <T>    业务操作的返回类型
         * @param tenant 租户编号
         * @param work   在事务中执行的业务操作
         * @return 业务操作的结果
         */
        <T> T execute(String tenant, Function<TradeSession, T> work);
    }
}
