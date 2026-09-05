package io.ddd4j.sample.javalin.cqrs.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.cqrs.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.cqrs.goods.domain.Goods;
import io.ddd4j.sample.javalin.cqrs.goods.domain.GoodsId;
import io.ddd4j.sample.javalin.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.javalin.cqrs.goods.domain.GoodsStatus;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * 商品 REST 控制器（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>通过 {@link EndpointGroup} 暴露路由注册入口，调用方在 Javalin 启动时通过
 * {@code javalinConfig.routes.apiBuilder(() -> goodsController.routes())} 注册。
 * 响应统一使用 ddd4j 的 {@link R}{@code <T>} 包装。
 *
 * <h3>路由列表</h3>
 * <table border="1">
 *   <tr><th>HTTP</th><th>路径</th><th>用途</th></tr>
 *   <tr><td>POST</td><td>/api/goods</td><td>创建商品</td></tr>
 *   <tr><td>PUT</td><td>/api/goods/{id}</td><td>更新商品</td></tr>
 *   <tr><td>PUT</td><td>/api/goods/{id}/status</td><td>调整商品状态</td></tr>
 *   <tr><td>DELETE</td><td>/api/goods/{id}</td><td>软删除商品</td></tr>
 *   <tr><td>GET</td><td>/api/goods/{id}</td><td>按 ID 查询</td></tr>
 *   <tr><td>GET</td><td>/api/goods/by-code</td><td>按编码查询</td></tr>
 *   <tr><td>GET</td><td>/api/goods/page</td><td>充血分页查询</td></tr>
 *   <tr><td>GET</td><td>/api/goods/list</td><td>充血列表查询</td></tr>
 *   <tr><td>GET</td><td>/api/goods/count</td><td>充血计数</td></tr>
 * </table>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GoodsController {

    private final GoodsApplicationService applicationService;

    public GoodsController(GoodsApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    /**
     * 从 Javalin Context 手动绑定 {@link GoodsQuery}（Javalin 7.x 不支持自动绑定 query 字段到 JavaBean）。
     */
    private static GoodsQuery bindQuery(Context ctx) {
        GoodsQuery query = new GoodsQuery();
        if (Objects.nonNull(ctx.queryParam("code"))) {
            query.setCode(ctx.queryParam("code"));
        }
        if (Objects.nonNull(ctx.queryParam("nameLike"))) {
            query.setNameLike(ctx.queryParam("nameLike"));
        }
        if (Objects.nonNull(ctx.queryParam("status"))) {
            query.setStatus(GoodsStatus.valueOf(ctx.queryParam("status")));
        }
        if (Objects.nonNull(ctx.queryParam("priceMin"))) {
            query.setPriceMin(new BigDecimal(ctx.queryParam("priceMin")));
        }
        if (Objects.nonNull(ctx.queryParam("priceMax"))) {
            query.setPriceMax(new BigDecimal(ctx.queryParam("priceMax")));
        }
        if (Objects.nonNull(ctx.queryParam("current"))) {
            query.setCurrent(Long.parseLong(ctx.queryParam("current")));
        }
        if (Objects.nonNull(ctx.queryParam("size"))) {
            query.setSize(Long.parseLong(ctx.queryParam("size")));
        }
        if (Objects.nonNull(ctx.queryParam("orderBys"))) {
            query.setOrderBys(ctx.queryParam("orderBys"));
        }
        return query;
    }

    /**
     * 以 {@link EndpointGroup} 形式暴露本控制器的全部路由。
     */
    public void routes() {
        // POST /api/goods —— 创建商品
        post("/api/goods", ctx -> {
            CreateGoodsRequest req = ctx.bodyAsClass(CreateGoodsRequest.class);
            Goods goods = applicationService.create(
                    req.getCode(), req.getName(), req.getPrice(), req.getStock());
            ctx.status(201).json(R.ok(goods));
        });

        // PUT /api/goods/{id} —— 更新商品
        put("/api/goods/{id}", ctx -> {
            Long id = ctx.pathParamAsClass("id", Long.class).get();
            UpdateGoodsRequest req = ctx.bodyAsClass(UpdateGoodsRequest.class);
            Goods goods = applicationService.update(
                    GoodsId.of(id), req.getName(), req.getPrice());
            ctx.json(R.ok(goods));
        });

        // PUT /api/goods/{id}/status —— 调整商品状态
        put("/api/goods/{id}/status", ctx -> {
            Long id = ctx.pathParamAsClass("id", Long.class).get();
            GoodsStatus status = ctx.queryParamAsClass("status", GoodsStatus.class).get();
            Goods goods = applicationService.changeStatus(GoodsId.of(id), status);
            ctx.json(R.ok(goods));
        });

        // DELETE /api/goods/{id} —— 软删除
        delete("/api/goods/{id}", ctx -> {
            Long id = ctx.pathParamAsClass("id", Long.class).get();
            applicationService.delete(GoodsId.of(id));
            ctx.json(R.ok());
        });

        // GET /api/goods/{id} —— 按 ID 查询
        get("/api/goods/{id}", ctx -> {
            Long id = ctx.pathParamAsClass("id", Long.class).get();
            ctx.json(R.ok(applicationService.getById(GoodsId.of(id))));
        });

        // GET /api/goods/by-code?code=xxx —— 按编码查询
        get("/api/goods/by-code", ctx -> {
            String code = ctx.queryParam("code");
            ctx.json(R.ok(applicationService.getByCode(code)));
        });

        // GET /api/goods/page —— 充血分页查询
        // Javalin 不会自动绑定 query 字段到 JavaBean，需要手动把 queryParam 拷到 GoodsQuery
        get("/api/goods/page", ctx -> {
            GoodsQuery query = bindQuery(ctx);
            ctx.json(R.ok(applicationService.pageQuery(query)));
        });

        // GET /api/goods/list —— 充血列表查询
        get("/api/goods/list", ctx -> {
            GoodsQuery query = bindQuery(ctx);
            ctx.json(R.ok(applicationService.listQuery(query)));
        });

        // GET /api/goods/count —— 充血计数
        get("/api/goods/count", ctx -> {
            GoodsQuery query = bindQuery(ctx);
            ctx.json(R.ok(applicationService.countQuery(query)));
        });
    }

    // ========================= 请求 DTO（轻量 record） =========================

    /**
     * 创建商品请求。
     */public final class CreateGoodsRequest {
        private final String code;
        private final String name;
        private final BigDecimal price;
        private final Integer stock;

        public CreateGoodsRequest(String code, String name, BigDecimal price, Integer stock) {
            this.code = code;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
        public String code() { return code; }
        public String name() { return name; }
        public BigDecimal price() { return price; }
        public Integer stock() { return stock; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateGoodsRequest other = (CreateGoodsRequest) o;
            return Objects.equals(this.code, other.code) && Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price) && Objects.equals(this.stock, other.stock);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(code, name, price, stock); }
        @Override
        public String toString() {
            return "CreateGoodsRequest{" + "code=" + code + ", " + "name=" + name + ", " + "price=" + price + ", " + "stock=" + stock + "}";
        }
    
    }

    /**
     * 更新商品请求。
     */public final class UpdateGoodsRequest {
        private final String name;
        private final BigDecimal price;

        public UpdateGoodsRequest(String name, BigDecimal price) {
            this.name = name;
            this.price = price;
        }
        public String name() { return name; }
        public BigDecimal price() { return price; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        UpdateGoodsRequest other = (UpdateGoodsRequest) o;
            return Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(name, price); }
        @Override
        public String toString() {
            return "UpdateGoodsRequest{" + "name=" + name + ", " + "price=" + price + "}";
        }
    
    }
}
