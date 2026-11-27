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
package io.ddd4j.sample.quarkus.satoken.goods.web;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.satoken.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.satoken.goods.domain.Goods;
import io.ddd4j.sample.quarkus.satoken.goods.domain.GoodsQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 商品充血查询资源（读侧）。
 *
 * <p>接收查询参数并构造 {@link GoodsQuery}，调用充血查询方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */

@Path("/api/goods")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@io.quarkus.arc.properties.IfBuildProperty(name = "sample.goods.standalone", stringValue = "true")
public class GoodsQueryResource {

    /** \u5904\u7406\u5546\u54c1\u521b\u5efa\u3001\u7ef4\u62a4\u548c\u67e5\u8be2\u64cd\u4f5c\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final GoodsApplicationService applicationService;

    /**
     * 创建商品查询资源。
     *
     * @param applicationService 商品应用服务
     */
    @Inject
    public GoodsQueryResource(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 充血分页查询。
     *
     * @param current  当前页码
     * @param size     每页数量
     * @param code     商品编码
     * @param nameLike 名称模糊匹配条件
     * @param status   商品状态
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
        return R.ok(applicationService.pageQuery(buildQuery(current, size, code, nameLike, status, priceMin, priceMax, orderBys)));
    }

    /**
     * 充血列表查询。
     *
     * @param code     商品编码
     * @param nameLike 名称模糊匹配条件
     * @param status   商品状态
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
        return R.ok(applicationService.listQuery(buildQuery(1L, 0L, code, nameLike, status, priceMin, priceMax, orderBys)));
    }

    /**
     * 充血计数。
     *
     * @param code     商品编码
     * @param nameLike 名称模糊匹配条件
     * @param status   商品状态
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
        return R.ok(applicationService.countQuery(buildQuery(1L, 0L, code, nameLike, status, priceMin, priceMax, null)));
    }

    /**
     * \u5c06\u5206\u9875\u548c\u7b5b\u9009\u8bf7\u6c42\u5c5e\u6027\u7ed1\u5b9a\u5230\u9886\u57df\u5546\u54c1\u67e5\u8be2\u3002
     * @param current \u8bf7\u6c42\u9875\u7801
     * @param size \u6bcf\u9875\u6700\u591a\u8fd4\u56de\u7684\u5546\u54c1\u8bb0\u5f55\u6570
     * @param code \u8bf7\u6c42\u4f20\u5165\u7684\u5546\u54c1\u7f16\u7801
     * @param nameLike \u5546\u54c1\u540d\u79f0\u6a21\u7cca\u5339\u914d\u6761\u4ef6
     * @param status \u5546\u54c1\u72b6\u6001\u6761\u4ef6\u6587\u672c
     * @param priceMin \u8bf7\u6c42\u4ef7\u683c\u533a\u95f4\u7684\u6700\u5c0f\u503c
     * @param priceMax \u8bf7\u6c42\u4ef7\u683c\u533a\u95f4\u7684\u6700\u5927\u503c
     * @param orderBys \u8bf7\u6c42\u7ed9\u51fa\u7684\u6392\u5e8f\u8868\u8fbe\u5f0f
     * @return \u53ef\u7531\u4ed3\u50a8\u6267\u884c\u7684\u5546\u54c1\u67e5\u8be2
     */
    private GoodsQuery buildQuery(Long current, Long size, String code, String nameLike, String status,
                                  BigDecimal priceMin, BigDecimal priceMax, String orderBys) {
        GoodsQuery query = new GoodsQuery();
        if (Objects.nonNull(current)) {
            query.setCurrent(current);
        }
        if (Objects.nonNull(size)) {
            query.setSize(size);
        }
        query.setCode(code);
        query.setNameLike(nameLike);
        query.setStatus(parseStatus(status));
        query.setPriceMin(priceMin);
        query.setPriceMax(priceMax);
        query.setOrderBys(orderBys);
        return query;
    }

    /**
     * \u628a\u63d0\u4f9b\u7684\u67e5\u8be2\u72b6\u6001\u8f6c\u6362\u4e3a\u5546\u54c1\u72b6\u6001\u679a\u4e3e\uff0c\u7a7a\u767d\u503c\u4e0d\u9650\u5236\u72b6\u6001\u3002
     * @param status \u5546\u54c1\u72b6\u6001\u6761\u4ef6\u6587\u672c
     * @return \u89e3\u6790\u5230\u7684\u5546\u54c1\u72b6\u6001\uff0c\u6ca1\u6709\u503c\u65f6\u4e3a\u7a7a
     */
    private io.ddd4j.sample.quarkus.satoken.goods.domain.GoodsStatus parseStatus(String status) {
        if (StrKit.isBlank(status)) {
            return null;
        }
        return io.ddd4j.sample.quarkus.satoken.goods.domain.GoodsStatus.valueOf(status.toUpperCase());
    }
}
