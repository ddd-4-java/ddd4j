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
package io.ddd4j.sample.javalin.satoken.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsId;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsStatus;
import io.ddd4j.sample.javalin.satoken.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.javalin.satoken.goods.web.dto.UpdateGoodsRequest;
import io.javalin.apibuilder.EndpointGroup;

import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * 商品 REST 资源（Javalin 适配，写侧）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GoodsResource {

    private final GoodsApplicationService applicationService;

    public GoodsResource(GoodsApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    public EndpointGroup routes() {
        return () -> {
            // POST /api/goods —— 创建商品
            post("/api/goods", ctx -> {
                CreateGoodsRequest req = ctx.bodyAsClass(CreateGoodsRequest.class);
                Goods goods = applicationService.create(
                        req.code(), req.name(), req.price(), req.stock());
                ctx.status(201).json(R.ok(goods));
            });

            // PUT /api/goods/{id} —— 更新商品
            put("/api/goods/{id}", ctx -> {
                Long id = Long.parseLong(ctx.pathParam("id"));
                UpdateGoodsRequest req = ctx.bodyAsClass(UpdateGoodsRequest.class);
                Goods goods = applicationService.update(
                        GoodsId.of(id), req.name(), req.price());
                ctx.json(R.ok(goods));
            });

            // PUT /api/goods/{id}/status?status=xxx —— 调整状态
            put("/api/goods/{id}/status", ctx -> {
                Long id = Long.parseLong(ctx.pathParam("id"));
                GoodsStatus status = GoodsStatus.valueOf(ctx.queryParam("status").toUpperCase());
                Goods goods = applicationService.changeStatus(GoodsId.of(id), status);
                ctx.json(R.ok(goods));
            });

            // DELETE /api/goods/{id} —— 软删
            delete("/api/goods/{id}", ctx -> {
                Long id = Long.parseLong(ctx.pathParam("id"));
                applicationService.delete(GoodsId.of(id));
                ctx.json(R.ok());
            });

            // GET /api/goods/by-code?code=xxx —— 按编码查询
            get("/api/goods/by-code", ctx -> {
                String code = ctx.queryParam("code");
                ctx.json(R.ok(applicationService.getByCode(code)));
            });

            // GET /api/goods/{id} —— 按 ID 查询
            get("/api/goods/{id}", ctx -> {
                Long id = Long.parseLong(ctx.pathParam("id"));
                ctx.json(R.ok(applicationService.getById(GoodsId.of(id))));
            });
        };
    }
}
