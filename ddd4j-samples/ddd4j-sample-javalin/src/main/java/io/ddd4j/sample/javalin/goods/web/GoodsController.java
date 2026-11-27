/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.javalin.goods.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.api.R;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.util.SFunction;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.goods.domain.Goods;
import io.ddd4j.sample.javalin.goods.domain.GoodsId;
import io.ddd4j.sample.javalin.goods.domain.GoodsQuery;
import io.ddd4j.sample.javalin.goods.domain.GoodsStatus;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.BadRequestResponse;
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
 * <h2>路由列表</h2>
 * <table border="1">
 *   <caption>商品 REST 路由</caption>
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

    /** \u5904\u7406\u5546\u54c1\u521b\u5efa\u3001\u7ef4\u62a4\u548c\u67e5\u8be2\u64cd\u4f5c\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final GoodsApplicationService applicationService;

    /**
     * 初始化 <code>GoodsController</code> 使用的 <code>applicationService</code>。
     *
     * @param applicationService \u5546\u54c1\u5e94\u7528\u670d\u52a1
     */
    public GoodsController(GoodsApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    /**
     * \u4ece Javalin Context \u624b\u52a8\u7ed1\u5b9a {@link GoodsQuery}\uff08Javalin 7.x \u4e0d\u652f\u6301\u81ea\u52a8\u7ed1\u5b9a query \u5b57\u6bb5\u5230 JavaBean\uff09\u3002
     *
     * @param ctx \u63d0\u4f9b\u67e5\u8be2\u53c2\u6570\u7684\u5f53\u524d\u8bf7\u6c42
     * @return \u6839\u636e\u5f53\u524d\u8bf7\u6c42\u53c2\u6570\u7ed1\u5b9a\u7684\u5546\u54c1\u67e5\u8be2
     */
    private static GoodsQuery bindQuery(Context ctx) {
        GoodsQuery query = new GoodsQuery();
        if (Objects.nonNull(ctx.queryParam("code"))) {
            query.setCode(ctx.queryParam("code"));
        }
        if (Objects.nonNull(ctx.queryParam("nameLike"))) {
            query.setNameLike(ctx.queryParam("nameLike"));
        }
        try {
            if (Objects.nonNull(ctx.queryParam("status"))) {
                query.setStatus(GoodsStatus.valueOf(ctx.queryParam("status")));
            }
            if (Objects.nonNull(ctx.queryParam("priceMin"))) {
                query.setPriceMin(new BigDecimal(ctx.queryParam("priceMin")));
            }
            if (Objects.nonNull(ctx.queryParam("priceMax"))) {
                query.setPriceMax(new BigDecimal(ctx.queryParam("priceMax")));
            }
        } catch (IllegalArgumentException invalidFilter) {
            throw new BadRequestResponse("invalid goods status or price filter");
        }
        if (Objects.nonNull(ctx.queryParam("current"))) {
            query.setCurrent(ctx.queryParamAsClass("current", Long.class).get());
        }
        if (Objects.nonNull(ctx.queryParam("size"))) {
            query.setSize(ctx.queryParamAsClass("size", Long.class).get());
        }
        String orderBys = ctx.queryParam("orderBys");
        if (StrKit.isNotBlank(orderBys)) {
            for (String orderBy : orderBys.split(",")) {
                addOrderBy(query, orderBy);
            }
        }
        return query;
    }

    /**
     * \u89e3\u6790\u8bf7\u6c42\u6392\u5e8f\u8868\u8fbe\u5f0f\u5e76\u8ffd\u52a0\u5230\u5f53\u524d\u5546\u54c1\u67e5\u8be2\u3002
     * @param query \u5f85\u5e94\u7528\u7684\u5546\u54c1\u7b5b\u9009\u548c\u6392\u5e8f\u6761\u4ef6
     * @param orderBy \u8bf7\u6c42\u4e2d\u4f20\u5165\u7684\u6392\u5e8f\u8868\u8fbe\u5f0f
     */
    private static void addOrderBy(GoodsQuery query, String orderBy) {
        String[] tokens = orderBy.trim().split("_");
        if (tokens.length != 2) {
            return;
        }
        SFunction<Goods, ?> property;
        switch (tokens[0]) {
            case "id":
                property = Goods::id;
                break;
            case "createTime":
                property = Goods::getCreateTime;
                break;
            case "updateTime":
                property = Goods::getUpdateTime;
                break;
            case "price":
                property = Goods::getPrice;
                break;
            default:
                property = null;
                break;
        }
        if (Objects.isNull(property)) {
            return;
        }
        if ("DESC".equalsIgnoreCase(tokens[1])) {
            query.orderByDesc(property);
            return;
        }
        query.orderByAsc(property);
    }

    /**
     * 以 {@link EndpointGroup} 形式暴露本控制器的全部路由。
     */
    public void routes() {
        // POST /api/goods —— 创建商品
        post("/api/goods", ctx -> {
            CreateGoodsRequest req = ctx.bodyAsClass(CreateGoodsRequest.class);
            Goods goods = applicationService.create(
                    req.code(), req.name(), req.price(), req.stock());
            ctx.status(201).json(R.ok(goods));
        });

        // PUT /api/goods/{id} —— 更新商品
        put("/api/goods/{id}", ctx -> {
            Long id = ctx.pathParamAsClass("id", Long.class).get();
            UpdateGoodsRequest req = ctx.bodyAsClass(UpdateGoodsRequest.class);
            Goods goods = applicationService.update(
                    GoodsId.of(id), req.name(), req.price());
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

        // 固定查询路径先于 /api/goods/{id} 注册，避免被当作商品 ID。
        // GET /api/goods/by-code?code=xxx —— 按编码查询
        get("/api/goods/by-code", ctx -> {
            String code = ctx.queryParamAsClass("code", String.class)
                    .check(StrKit::isNotBlank, "goods code must not be blank").get();
            Goods goods;
            try {
                goods = applicationService.getByCode(code);
            } catch (BizRuntimeException missingGoods) {
                throw new BadRequestResponse("goods code not found");
            }
            ctx.json(R.ok(goods));
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

        // GET /api/goods/{id} —— 按 ID 查询
        get("/api/goods/{id}", ctx -> {
            Long id = ctx.pathParamAsClass("id", Long.class).get();
            ctx.json(R.ok(applicationService.getById(GoodsId.of(id))));
        });
    }

    // ========================= 请求 DTO =========================

    /**
     * 创建商品请求。
     */
    public static class CreateGoodsRequest {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u4e1a\u52a1\u7f16\u7801\u3002 */
        private String code;

        /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;

        /** \u5546\u54c1\u793a\u4f8b\u4f7f\u7528\u7684\u9500\u552e\u4ef7\u683c\u3002 */
        private BigDecimal price;

        /** \u793a\u4f8b\u5546\u54c1\u53ef\u7528\u7684\u5e93\u5b58\u6570\u91cf\u3002 */
        private Integer stock;

        /**
         * 创建空请求，供反序列化使用。
         */
        public CreateGoodsRequest() {
        }

        /**
         * 初始化 <code>CreateGoodsRequest</code> 使用的 <code>code</code>、<code>name</code>、<code>price</code>、<code>stock</code>。
         *
         * @param code \u5546\u54c1\u7f16\u7801
         * @param name \u5546\u54c1\u540d\u79f0
         * @param price \u5546\u54c1\u4ef7\u683c
         * @param stock \u5e93\u5b58\u6570\u91cf
         */
        @JsonCreator()
        public CreateGoodsRequest(@JsonProperty("code") String code, @JsonProperty("name") String name, @JsonProperty("price") BigDecimal price, @JsonProperty("stock") Integer stock) {
            this.code = code;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }

        /** @return \u5546\u54c1\u7f16\u7801 */
        @JsonProperty("code")
        public String code() {
            return code;
        }

        /** @return \u5546\u54c1\u540d\u79f0 */
        @JsonProperty("name")
        public String name() {
            return name;
        }

        /** @return \u5546\u54c1\u4ef7\u683c */
        @JsonProperty("price")
        public BigDecimal price() {
            return price;
        }

        /** @return \u5e93\u5b58\u6570\u91cf */
        @JsonProperty("stock")
        public Integer stock() {
            return stock;
        }

        /** @return \u5546\u54c1\u7f16\u7801 */
        public String getCode() {
            return code;
        }

        /** @param code \u5546\u54c1\u7f16\u7801 */
        public void setCode(String code) {
            this.code = code;
        }

        /** @return \u5546\u54c1\u540d\u79f0 */
        public String getName() {
            return name;
        }

        /** @param name \u5546\u54c1\u540d\u79f0 */
        public void setName(String name) {
            this.name = name;
        }

        /** @return \u5546\u54c1\u4ef7\u683c */
        public BigDecimal getPrice() {
            return price;
        }

        /** @param price \u5546\u54c1\u4ef7\u683c */
        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        /** @return \u5e93\u5b58\u6570\u91cf */
        public Integer getStock() {
            return stock;
        }

        /** @param stock \u5e93\u5b58\u6570\u91cf */
        public void setStock(Integer stock) {
            this.stock = stock;
        }

        /**
         * 判断另一对象是否可以参与相等比较。
         *
         * @param other 待比较对象
         * @return 是否为同类请求或响应
         */
        protected boolean canEqual(Object other) {
            return other instanceof CreateGoodsRequest;
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CreateGoodsRequest)) {
                return false;
            }
            CreateGoodsRequest other = (CreateGoodsRequest) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(getCode(), other.getCode())
                    && Objects.equals(getName(), other.getName())
                    && Objects.equals(getPrice(), other.getPrice())
                    && Objects.equals(getStock(), other.getStock());
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(getCode()) ? 43 : getCode().hashCode());
            result = 59 * result + (Objects.isNull(getName()) ? 43 : getName().hashCode());
            result = 59 * result + (Objects.isNull(getPrice()) ? 43 : getPrice().hashCode());
            result = 59 * result + (Objects.isNull(getStock()) ? 43 : getStock().hashCode());
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>code</code>、<code>name</code>、<code>price</code>、<code>stock</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "GoodsController.CreateGoodsRequest(code=" + getCode() + ", name=" + getName() + ", price=" + getPrice() + ", stock=" + getStock() + ")";
        }
    }

    /**
     * 更新商品请求。
     */
    public static class UpdateGoodsRequest {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;

        /** \u7528\u4e8e\u66ff\u6362\u5546\u54c1\u5f53\u524d\u9500\u552e\u4ef7\u7684\u8bf7\u6c42\u5b57\u6bb5\u3002 */
        private BigDecimal price;

        /**
         * 创建空请求，供反序列化使用。
         */
        public UpdateGoodsRequest() {
        }

        /**
         * 初始化 <code>UpdateGoodsRequest</code> 使用的 <code>name</code>、<code>price</code>。
         *
         * @param name \u65b0\u5546\u54c1\u540d\u79f0
         * @param price \u65b0\u5546\u54c1\u4ef7\u683c
         */
        @JsonCreator()
        public UpdateGoodsRequest(@JsonProperty("name") String name, @JsonProperty("price") BigDecimal price) {
            this.name = name;
            this.price = price;
        }

        /** @return \u65b0\u5546\u54c1\u540d\u79f0 */
        @JsonProperty("name")
        public String name() {
            return name;
        }

        /** @return \u65b0\u5546\u54c1\u4ef7\u683c */
        @JsonProperty("price")
        public BigDecimal price() {
            return price;
        }

        /** @return \u5546\u54c1\u540d\u79f0 */
        public String getName() {
            return name;
        }

        /** @param name \u5546\u54c1\u540d\u79f0 */
        public void setName(String name) {
            this.name = name;
        }

        /** @return \u5546\u54c1\u4ef7\u683c */
        public BigDecimal getPrice() {
            return price;
        }

        /** @param price \u5546\u54c1\u4ef7\u683c */
        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        /**
         * 判断另一对象是否可以参与相等比较。
         *
         * @param other 待比较对象
         * @return 是否为同类请求或响应
         */
        protected boolean canEqual(Object other) {
            return other instanceof UpdateGoodsRequest;
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof UpdateGoodsRequest)) {
                return false;
            }
            UpdateGoodsRequest other = (UpdateGoodsRequest) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(getName(), other.getName())
                    && Objects.equals(getPrice(), other.getPrice());
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(getName()) ? 43 : getName().hashCode());
            result = 59 * result + (Objects.isNull(getPrice()) ? 43 : getPrice().hashCode());
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>name</code>、<code>price</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "GoodsController.UpdateGoodsRequest(name=" + getName() + ", price=" + getPrice() + ")";
        }
    }
}
