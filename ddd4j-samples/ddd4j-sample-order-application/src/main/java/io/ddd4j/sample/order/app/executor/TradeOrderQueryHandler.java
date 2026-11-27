package io.ddd4j.sample.order.app.executor;

import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.RequiredArgsConstructor;

/** 处理租户内订单查询。 */
@RequiredArgsConstructor
public final class TradeOrderQueryHandler implements QueryHandler<TradeOrderQuery, TradeOrder> {
    /** \u67e5\u8be2\u8ba2\u5355\u805a\u5408\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final CatalogService service;

    /** {@inheritDoc} */
    @Override
    public Class<TradeOrderQuery> queryType() {
        return TradeOrderQuery.class;
    }

    /** {@inheritDoc} */
    @Override
    public TradeOrder handle(TradeOrderQuery query) {
        return service.order(query);
    }
}
