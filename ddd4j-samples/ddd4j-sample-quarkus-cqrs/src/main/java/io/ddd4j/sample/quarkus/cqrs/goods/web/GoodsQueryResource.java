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
package io.ddd4j.sample.quarkus.cqrs.goods.web;

import javax.enterprise.context.ApplicationScoped;
import io.ddd4j.core.api.Page;
import io.ddd4j.sample.quarkus.cqrs.goods.application.GoodsApplicationService;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.Goods;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.cqrs.goods.web.dto.GoodsQueryParameters;
import io.ddd4j.web.quarkus.TenantAwareResource;
import javax.inject.Inject;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;

/**
 * 商品 CQRS 查询端点（第三轨：Model/Query 快速 CRUD 模式，查询侧）。
 *
 * <p>与 {@link GoodsResource}（命令端）形成 CQRS 对照：
 * <ul>
 *   <li>命令端（GoodsResource）：处理创建/更新/删除等写操作</li>
 *   <li>查询端（本类）：基于 {@link GoodsQuery} 充血查询做分页/列表/统计</li>
 * </ul>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /query/goods/page}  - 充血分页查询（绑定 GoodsQuery 字段）</li>
 *   <li>{@code GET /query/goods/list}  - 充血列表查询</li>
 *   <li>{@code GET /query/goods/count} - 充血计数查询</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <p>查询端不修改商品状态，只读取数据。通过 {@link GoodsQuery} 的充血方法
 * （{@code page()} / {@code list()} / {@code count()}）实现无模板 CRUD 查询，
 * 展示 ddd4j Query 在 Quarkus JAX-RS 下的集成方式。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/query/goods")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class GoodsQueryResource extends TenantAwareResource {

    private final GoodsApplicationService applicationService;

    @Inject
    public GoodsQueryResource(GoodsApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 充血分页查询：直接接收 {@link GoodsQuery}，
     * 由 Quarkus REST 绑定字段后调用 {@code query.page()}。
     *
     * <p>支持通过 query string 传入 {@code code} / {@code nameLike} /
     * {@code status} / {@code priceMin} / {@code priceMax} / {@code current} /
     * {@code size} / {@code orderBys} 等条件。
     *
     * <pre>
     * GET /query/goods/page?status=ON_SALE&amp;current=1&amp;size=20&amp;orderBys=createTime_DESC
     * </pre>
     *
     * @param parameters 商品查询参数（由 JAX-RS 自动绑定）
     * @return 分页结果
     */
    @GET
    @Path("/page")
    public Response page(@BeanParam GoodsQueryParameters parameters) {
        Page<Goods> page = applicationService.pageQuery(parameters.toQuery());
        return ok(page);
    }

    /**
     * 充血列表查询。
     *
     * <pre>
     * GET /query/goods/list?nameLike=iPhone
     * </pre>
     *
     * @param parameters 商品查询参数
     * @return 商品列表
     */
    @GET
    @Path("/list")
    public Response list(@BeanParam GoodsQueryParameters parameters) {
        List<Goods> products = applicationService.listQuery(parameters.toQuery());
        return ok(products);
    }

    /**
     * 充血计数。
     *
     * <pre>
     * GET /query/goods/count?status=ON_SALE
     * </pre>
     *
     * @param parameters 商品查询参数
     * @return 总数
     */
    @GET
    @Path("/count")
    public Response count(@BeanParam GoodsQueryParameters parameters) {
        long count = applicationService.countQuery(parameters.toQuery());
        return ok(count);
    }
}
