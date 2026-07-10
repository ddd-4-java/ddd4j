package io.ddd4j.sample.javalin.satoken.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsQuery;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsStatus;
import io.javalin.apibuilder.EndpointGroup;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * 商品充血查询资源（Javalin 适配，读侧）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GoodsQueryResource {

    private final GoodsApplicationService applicationService;

    public GoodsQueryResource(GoodsApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    public EndpointGroup routes() {
        return () -> {
            // GET /api/goods/page
            get("/api/goods/page", ctx -> {
                GoodsQuery query = buildQuery(ctx);
                if (ctx.queryParam("current") != null) {
                    query.setCurrent(Long.parseLong(ctx.queryParam("current")));
                }
                if (ctx.queryParam("size") != null) {
                    query.setSize(Long.parseLong(ctx.queryParam("size")));
                }
                ctx.json(R.ok(applicationService.pageQuery(query)));
            });

            // GET /api/goods/list
            get("/api/goods/list", ctx -> {
                GoodsQuery query = buildQuery(ctx);
                List<Goods> list = applicationService.listQuery(query);
                ctx.json(R.ok(list));
            });

            // GET /api/goods/count
            get("/api/goods/count", ctx -> {
                GoodsQuery query = buildQuery(ctx);
                ctx.json(R.ok(applicationService.countQuery(query)));
            });
        };
    }

    private GoodsQuery buildQuery(io.javalin.http.Context ctx) {
        GoodsQuery query = new GoodsQuery();
        query.setCode(ctx.queryParam("code"));
        query.setNameLike(ctx.queryParam("nameLike"));
        String status = ctx.queryParam("status");
        if (status != null && !status.isBlank()) {
            query.setStatus(GoodsStatus.valueOf(status.toUpperCase()));
        }
        String priceMin = ctx.queryParam("priceMin");
        if (priceMin != null && !priceMin.isBlank()) {
            query.setPriceMin(new BigDecimal(priceMin));
        }
        String priceMax = ctx.queryParam("priceMax");
        if (priceMax != null && !priceMax.isBlank()) {
            query.setPriceMax(new BigDecimal(priceMax));
        }
        query.setOrderBys(ctx.queryParam("orderBys"));
        return query;
    }
}