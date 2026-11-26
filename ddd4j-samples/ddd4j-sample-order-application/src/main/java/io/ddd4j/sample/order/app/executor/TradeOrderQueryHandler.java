package io.ddd4j.sample.order.app.executor;

import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TradeOrderQueryHandler implements QueryHandler<TradeOrderQuery, TradeOrder> {
    private final CatalogService service;

    @Override
    public Class<TradeOrderQuery> queryType() {
        return TradeOrderQuery.class;
    }

    @Override
    public TradeOrder handle(TradeOrderQuery query) {
        return service.order(query);
    }
}
