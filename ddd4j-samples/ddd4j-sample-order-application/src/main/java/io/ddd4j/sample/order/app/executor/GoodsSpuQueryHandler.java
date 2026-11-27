package io.ddd4j.sample.order.app.executor;

import io.ddd4j.core.cqrs.query.QueryHandler;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.client.query.GoodsSpuQuery;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import lombok.RequiredArgsConstructor;

/**
 * 处理租户内商品查询。
 */
@RequiredArgsConstructor
public final class GoodsSpuQueryHandler implements QueryHandler<GoodsSpuQuery, GoodsSpu> {
    /**
     * 查询商品聚合的应用服务。
     */
    private final CatalogService service;

    /**
     * 声明当前查询处理器接受的查询类型。
     *
     * @return 本处理器接受的查询类型
     */
    @Override
    public Class<GoodsSpuQuery> queryType() {
        return GoodsSpuQuery.class;
    }

    /**
     * 处理调用方传入的消息或请求。
     *
     * <p>此实现调用 <code>service.goods(query)</code>。</p>
     *
     * @param query 查询对象
     * @return 实际返回<code>service.goods(query)</code>
     */
    @Override
    public GoodsSpu handle(GoodsSpuQuery query) {
        return service.goods(query);
    }
}
