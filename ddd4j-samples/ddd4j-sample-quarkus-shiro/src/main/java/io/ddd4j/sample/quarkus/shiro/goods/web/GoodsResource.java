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
package io.ddd4j.sample.quarkus.shiro.goods.web;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.sample.quarkus.shiro.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.shiro.goods.domain.Goods;
import io.ddd4j.sample.quarkus.shiro.goods.domain.GoodsId;
import io.ddd4j.sample.quarkus.shiro.goods.domain.GoodsStatus;
import io.ddd4j.sample.quarkus.shiro.goods.web.dto.CreateGoodsRequest;
import io.ddd4j.sample.quarkus.shiro.goods.web.dto.UpdateGoodsRequest;
import java.math.BigDecimal;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * 商品 JAX-RS 资源（读写 HTTP 入口）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/api/goods")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GoodsResource {

    private final GoodsApplicationService applicationService;
    private final GoodsQueryResource queryResource;

    /**
     * 保留单参数构造入口，供现有直接调用方使用。
     *
     * @param applicationService 商品应用服务
     */
    public GoodsResource(GoodsApplicationService applicationService) {
        this(applicationService, new GoodsQueryResource(applicationService));
    }

    /**
     * 创建商品写入资源。
     *
     * @param applicationService 商品应用服务
     * @param queryResource 商品查询逻辑
     */
    @Inject
    public GoodsResource(GoodsApplicationService applicationService, GoodsQueryResource queryResource) {
        this.applicationService = applicationService;
        this.queryResource = queryResource;
    }

    /**
     * 创建商品。
     *
     * @param request 请求数据
     * @return 创建后的商品
     */
    @POST
    public R<Goods> create(CreateGoodsRequest request) {
        Goods goods = applicationService.create(
                request.code(), request.name(), request.price(), request.stock());
        return R.ok(goods);
    }

    /**
     * 更新商品。
     *
     * @param id 商品标识
     * @param request 请求数据
     * @return 更新后的商品
     */
    @PUT
    @Path("/{id}")
    public R<Goods> update(@PathParam("id") Long id, UpdateGoodsRequest request) {
        Goods goods = applicationService.update(
                GoodsId.of(id), request.name(), request.price());
        return R.ok(goods);
    }

    /**
     * 调整商品状态。
     *
     * @param id 商品标识
     * @param status 商品状态
     * @return 更新后的商品
     */
    @PUT
    @Path("/{id}/status")
    public R<Goods> changeStatus(@PathParam("id") Long id, @QueryParam("status") GoodsStatus status) {
        Goods goods = applicationService.changeStatus(GoodsId.of(id), status);
        return R.ok(goods);
    }

    /**
     * 删除商品（软删）。
     *
     * @param id 商品标识
     * @return 响应结果
     */
    @DELETE
    @Path("/{id}")
    public R<Void> delete(@PathParam("id") Long id) {
        applicationService.delete(GoodsId.of(id));
        return R.ok();
    }

    /**
     * 按 ID 查询商品。
     *
     * @param id 商品标识
     * @return 商品
     */
    @GET
    @Path("/{id}")
    public R<Goods> getById(@PathParam("id") Long id) {
        return R.ok(applicationService.getById(GoodsId.of(id)));
    }

    /**
     * 按编码查询商品。
     *
     * @param code 商品编码
     * @return 商品
     */
    @GET
    @Path("/by-code")
    public R<Goods> getByCode(@QueryParam("code") String code) {
        return R.ok(applicationService.getByCode(code));
    }

    /**
     * 按条件分页查询商品。
     *
     * @param current 当前页码
     * @param size 每页数量
     * @param code 商品编码
     * @param nameLike 名称模糊匹配条件
     * @param status 商品状态
     * @param priceMin 最低价格
     * @param priceMax 最高价格
     * @param orderBys 排序字段
     * @return 商品分页响应
     */
    @GET
    @Path("/page")
    public R<Page<Goods>> page(
            @QueryParam("current") Long current,
            @QueryParam("size") Long size,
            @QueryParam("code") String code,
            @QueryParam("nameLike") String nameLike,
            @QueryParam("status") String status,
            @QueryParam("priceMin") BigDecimal priceMin,
            @QueryParam("priceMax") BigDecimal priceMax,
            @QueryParam("orderBys") String orderBys) {
        return queryResource.page(current, size, code, nameLike, status, priceMin, priceMax, orderBys);
    }

    /**
     * 按条件列出商品。
     *
     * @param code 商品编码
     * @param nameLike 名称模糊匹配条件
     * @param status 商品状态
     * @param priceMin 最低价格
     * @param priceMax 最高价格
     * @param orderBys 排序字段
     * @return 商品列表响应
     */
    @GET
    @Path("/list")
    public R<List<Goods>> list(
            @QueryParam("code") String code,
            @QueryParam("nameLike") String nameLike,
            @QueryParam("status") String status,
            @QueryParam("priceMin") BigDecimal priceMin,
            @QueryParam("priceMax") BigDecimal priceMax,
            @QueryParam("orderBys") String orderBys) {
        return queryResource.list(code, nameLike, status, priceMin, priceMax, orderBys);
    }

    /**
     * 按条件统计商品。
     *
     * @param code 商品编码
     * @param nameLike 名称模糊匹配条件
     * @param status 商品状态
     * @param priceMin 最低价格
     * @param priceMax 最高价格
     * @return 商品数量响应
     */
    @GET
    @Path("/count")
    public R<Long> count(
            @QueryParam("code") String code,
            @QueryParam("nameLike") String nameLike,
            @QueryParam("status") String status,
            @QueryParam("priceMin") BigDecimal priceMin,
            @QueryParam("priceMax") BigDecimal priceMax) {
        return queryResource.count(code, nameLike, status, priceMin, priceMax);
    }
}
