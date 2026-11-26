package io.ddd4j.sample.order.app.executor;

import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.client.query.GoodsSpuQuery;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import lombok.RequiredArgsConstructor;

/** 处理租户内商品查询。 */
@RequiredArgsConstructor
public final class GoodsSpuQueryHandler implements QueryHandler<GoodsSpuQuery, GoodsSpu> {
    private final CatalogService service;

    @Override
    public Class<GoodsSpuQuery> queryType() {
        return GoodsSpuQuery.class;
    }

    @Override
    public GoodsSpu handle(GoodsSpuQuery query) {
        return service.goods(query);
    }
}
