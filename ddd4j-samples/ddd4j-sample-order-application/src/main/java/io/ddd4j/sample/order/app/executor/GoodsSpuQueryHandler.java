package io.ddd4j.sample.order.app.executor;

import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.client.query.GoodsSpuQuery;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import lombok.RequiredArgsConstructor;

/** 处理租户内商品查询。 */
@RequiredArgsConstructor
public final class GoodsSpuQueryHandler implements QueryHandler<GoodsSpuQuery, GoodsSpu> {
    /** \u67e5\u8be2\u5546\u54c1\u805a\u5408\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final CatalogService service;

    /** {@inheritDoc} */
    @Override
    public Class<GoodsSpuQuery> queryType() {
        return GoodsSpuQuery.class;
    }

    /** {@inheritDoc} */
    @Override
    public GoodsSpu handle(GoodsSpuQuery query) {
        return service.goods(query);
    }
}
