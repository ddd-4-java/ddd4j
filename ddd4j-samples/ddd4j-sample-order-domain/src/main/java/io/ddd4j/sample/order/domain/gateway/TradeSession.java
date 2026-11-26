package io.ddd4j.sample.order.domain.gateway;

import java.util.function.Function;

/**
 * 一次业务调用内共用同一个数据库事务。
 */
public interface TradeSession {
    GoodsSpuRepository goods();

    TradeOrderRepository orders();

    interface Transactions {
        <T> T execute(String tenant, Function<TradeSession, T> work);
    }
}
