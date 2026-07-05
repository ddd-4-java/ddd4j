package io.ddd4j.sample.javalin.cqrs.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.cqrs.cache.GoodsCacheService;
import io.ddd4j.sample.javalin.cqrs.goods.domain.Goods;
import io.ddd4j.sample.javalin.cqrs.goods.domain.GoodsRepository;
import io.javalin.apibuilder.EndpointGroup;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * 商品 CQRS 读侧控制器 - 缓存优先版本（Javalin）。
 *
 * <p>本控制器是 CQRS 示例相对于基线 {@code GoodsController} 的增强：
 * <ul>
 *   <li>按 ID 查询走 {@link GoodsCacheService} 缓存</li>
 *   <li>列表查询走缓存</li>
 *   <li>按状态过滤走缓存</li>
 *   <li>暴露 cache-stats 端点观察 CQRS 缓存命中率</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GoodsReadController {

    private final GoodsCacheService goodsCacheService;
    private final GoodsRepository goodsRepository;

    public GoodsReadController(GoodsCacheService goodsCacheService, GoodsRepository goodsRepository) {
        this.goodsCacheService = Objects.requireNonNull(goodsCacheService, "goodsCacheService must not be null");
        this.goodsRepository = Objects.requireNonNull(goodsRepository, "goodsRepository must not be null");
    }

    /**
     * 路由注册入口。
     */
    public void routes() {
        // GET /api/goods/query/by-id/{id}
        get("/api/goods/query/by-id/{id}", ctx -> {
            Long id = ctx.pathParamAsClass("id", Long.class).get();
            try {
                ctx.json(R.ok(goodsCacheService.getById(id)));
            } catch (IllegalArgumentException e) {
                ctx.status(404).json(R.fail("404", e.getMessage()));
            }
        });

        // GET /api/goods/query/by-code/{code}
        get("/api/goods/query/by-code/{code}", ctx -> {
            String code = ctx.pathParam("code");
            ctx.json(goodsRepository.findByCode(code)
                    .<io.ddd4j.core.api.R<Goods>>map(R::ok)
                    .orElse(R.fail("404", "goods not found: " + code)));
        });

        // GET /api/goods/query/list
        get("/api/goods/query/list", ctx -> ctx.json(R.ok(goodsCacheService.listAll())));

        // GET /api/goods/query/by-status?status=ON_SALE
        get("/api/goods/query/by-status", ctx -> {
            String status = ctx.queryParam("status");
            ctx.json(R.ok(goodsCacheService.listByStatus(status)));
        });

        // GET /api/goods/query/count
        get("/api/goods/query/count", ctx -> {
            ctx.json(R.ok((long) goodsCacheService.listAll().size()));
        });

        // GET /api/goods/query/cache-stats
        get("/api/goods/query/cache-stats", ctx -> {
            ctx.json(R.ok(goodsCacheService.stats()));
        });
    }
}