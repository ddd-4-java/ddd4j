package io.ddd4j.sample.order.app.executor;

import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.RequiredArgsConstructor;

/**
 * 处理租户内订单查询。
 */
@RequiredArgsConstructor
public final class TradeOrderQueryHandler implements QueryHandler<TradeOrderQuery, TradeOrder> {
    /**
     * 查询订单聚合的应用服务。
     */
    private final CatalogService service;

    /**
     * 声明当前查询处理器接受的查询类型。
     *
     * @return 本处理器接受的查询类型
     */
    @Override
    public Class<TradeOrderQuery> queryType() {
        return TradeOrderQuery.class;
    }

    /**
     * 处理调用方传入的消息或请求。
     *
     * <p>此实现调用 <code>service.order(query)</code>。</p>
     *
     * @param query 查询对象
     * @return 实际返回<code>service.order(query)</code>
     */
    @Override
    public TradeOrder handle(TradeOrderQuery query) {
        return service.order(query);
    }
}
