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
package io.ddd4j.sample.quarkus.goods.web;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.goods.domain.Goods;
import io.ddd4j.sample.quarkus.goods.domain.GoodsId;
import io.ddd4j.sample.quarkus.goods.domain.GoodsStatus;
import io.ddd4j.sample.quarkus.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.quarkus.goods.web.dto.GoodsResponse;
import io.ddd4j.sample.quarkus.goods.web.dto.UpdateGoodsRequest;
import io.ddd4j.web.quarkus.TenantAwareResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Locale;

/**
 * 商品 JAX-RS 资源（第三轨：Model/Query 命令端）。
 *
 * <p>演示 ddd4j 第三轨的"轻量 CRUD"风格：所有写操作直接由应用服务
 * 编排仓储完成，聚合根不参与业务方法（与 {@code OrderResource} 形成对比）。
 *
 * <p>继承 {@link TenantAwareResource}，可直接调用 {@code getTenantId()} /
 * {@code getLang()} / {@code ok(data)} / {@code fail(msg)} 等辅助方法，
 * 同时复用 ddd4j 的统一响应包装 {@link R}。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code POST   /goods}                 - 创建商品</li>
 *   <li>{@code PUT    /goods/{id}}             - 更新商品</li>
 *   <li>{@code PUT    /goods/{id}/status}      - 调整商品状态</li>
 *   <li>{@code DELETE /goods/{id}}             - 软删除商品</li>
 *   <li>{@code GET    /goods/{id}}             - 按 ID 查询（命令端直读）</li>
 *   <li>{@code GET    /goods/by-code}          - 按编码查询</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <p>Resource 层只做协议适配（HTTP ↔ 应用服务入参 / 响应），不包含业务规则。
 * 所有业务方法都委托给 {@link GoodsApplicationService}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/goods")
@Produces(MediaType.APPLICATION_JSON)
public class GoodsResource extends TenantAwareResource {

    private final GoodsApplicationService applicationService;

    @Inject
    public GoodsResource(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建商品。
     *
     * <pre>
     * POST /goods
     * { "code": "SKU-001", "name": "iPhone 15", "price": 5999.00, "stock": 100 }
     * </pre>
     *
     * @param request 创建请求
     * @return 创建的商品
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateGoodsRequest request) {
        Goods product = applicationService.create(
                request.code(), request.name(), request.price(), request.stock());
        return ok(GoodsResponse.from(product));
    }

    /**
     * 更新商品。
     *
     * <pre>
     * PUT /goods/{id}
     * { "name": "iPhone 15 Pro", "price": 7999.00 }
     * </pre>
     *
     * @param id      商品 ID
     * @param request 更新请求
     * @return 更新后的商品
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") Long id, UpdateGoodsRequest request) {
        Goods product = applicationService.update(
                GoodsId.of(id), request.name(), request.price());
        return ok(GoodsResponse.from(product));
    }

    /**
     * 调整商品状态。
     *
     * <pre>
     * PUT /goods/{id}/status?status=ON_SALE
     * </pre>
     *
     * @param id     商品 ID
     * @param status 新状态
     * @return 更新后的商品
     */
    @PUT
    @Path("/{id}/status")
    public Response changeStatus(@PathParam("id") Long id, @QueryParam("status") String status) {
        Goods product = applicationService.changeStatus(GoodsId.of(id), parseStatus(status));
        return ok(GoodsResponse.from(product));
    }

    /**
     * 软删除商品。
     *
     * @param id 商品 ID
     * @return 成功响应
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        applicationService.delete(GoodsId.of(id));
        return ok();
    }

    /**
     * 按 ID 查询商品。
     *
     * @param id 商品 ID
     * @return 商品
     */
    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        return ok(GoodsResponse.from(applicationService.getById(GoodsId.of(id))));
    }

    /**
     * 按编码查询商品。
     *
     * @param code 商品编码
     * @return 商品
     */
    @GET
    @Path("/by-code")
    public Response getByCode(@QueryParam("code") String code) {
        return ok(GoodsResponse.from(applicationService.getByCode(code)));
    }

    private GoodsStatus parseStatus(String status) {
        if (StrKit.isBlank(status)) {
            throw new IllegalArgumentException("status must not be blank");
        }
        return GoodsStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }
}
