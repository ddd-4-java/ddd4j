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
package io.ddd4j.sample.quarkus.goods.web.dto;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.goods.domain.GoodsStatus;
import jakarta.ws.rs.QueryParam;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * HTTP query parameters translated into the framework-independent goods query.
 */
@Getter
@Setter
public class GoodsQueryParameters {

    /**
     * 当前请求的查询页号。
     */
    @QueryParam("current")
    private Long current;
    /**
     * 当前分页查询希望返回的记录数。
     */
    @QueryParam("size")
    private Long size;
    /**
     * 当前请求或结果使用的业务编码。
     */
    @QueryParam("code")
    private String code;
    /**
     * 商品名称模糊匹配条件。
     */
    @QueryParam("nameLike")
    private String nameLike;
    /**
     * 当前商品、账号或权限记录的业务状态。
     */
    @QueryParam("status")
    private String status;
    /**
     * 筛选商品时的价格下限。
     */
    @QueryParam("priceMin")
    private BigDecimal priceMin;
    /**
     * 筛选商品时的价格上限。
     */
    @QueryParam("priceMax")
    private BigDecimal priceMax;
    /**
     * 可解析为排序字段和方向的查询文本。
     */
    @QueryParam("orderBys")
    private String orderBys;

    /**
     * @return 根据 HTTP 参数构造的商品查询对象
     */
    public GoodsQuery toQuery() {
        GoodsQuery query = new GoodsQuery()
                .setCode(code)
                .setNameLike(nameLike)
                .setStatus(parseStatus())
                .setPriceMin(priceMin)
                .setPriceMax(priceMax);
        if (Objects.nonNull(current)) {
            query.setCurrent(current);
        }
        if (Objects.nonNull(size)) {
            query.setSize(size);
        }
        if (StrKit.isNotBlank(orderBys)) {
            query.setOrderBys(orderBys);
        }
        return query;
    }

    /**
     * 把提供的查询状态转换为商品状态枚举，空白值不限制状态。
     *
     * @return 解析到的商品状态，没有值时为空
     */
    private GoodsStatus parseStatus() {
        return StrKit.isBlank(status) ? null : GoodsStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }
}
