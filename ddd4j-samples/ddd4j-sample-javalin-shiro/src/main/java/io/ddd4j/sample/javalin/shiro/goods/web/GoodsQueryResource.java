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
package io.ddd4j.sample.javalin.shiro.goods.web;

import io.ddd4j.kit.lang.StrKit;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.shiro.goods.domain.Goods;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsQuery;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsStatus;
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
                if (Objects.nonNull(ctx.queryParam("current"))) {
                    query.setCurrent(Long.parseLong(ctx.queryParam("current")));
                }
                if (Objects.nonNull(ctx.queryParam("size"))) {
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
        if (Objects.nonNull(status) && StrKit.isNotBlank(status)) {
            query.setStatus(GoodsStatus.valueOf(status.toUpperCase()));
        }
        String priceMin = ctx.queryParam("priceMin");
        if (Objects.nonNull(priceMin) && StrKit.isNotBlank(priceMin)) {
            query.setPriceMin(new BigDecimal(priceMin));
        }
        String priceMax = ctx.queryParam("priceMax");
        if (Objects.nonNull(priceMax) && StrKit.isNotBlank(priceMax)) {
            query.setPriceMax(new BigDecimal(priceMax));
        }
        query.setOrderBys(ctx.queryParam("orderBys"));
        return query;
    }
}