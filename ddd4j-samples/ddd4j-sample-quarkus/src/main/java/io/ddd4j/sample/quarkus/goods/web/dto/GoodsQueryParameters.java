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

    /** \u5f53\u524d\u8bf7\u6c42\u7684\u67e5\u8be2\u9875\u53f7\u3002 */
    @QueryParam("current")
    private Long current;
    /** \u5f53\u524d\u5206\u9875\u67e5\u8be2\u5e0c\u671b\u8fd4\u56de\u7684\u8bb0\u5f55\u6570\u3002 */
    @QueryParam("size")
    private Long size;
    /** \u5f53\u524d\u8bf7\u6c42\u6216\u7ed3\u679c\u4f7f\u7528\u7684\u4e1a\u52a1\u7f16\u7801\u3002 */
    @QueryParam("code")
    private String code;
    /** \u5546\u54c1\u540d\u79f0\u6a21\u7cca\u5339\u914d\u6761\u4ef6\u3002 */
    @QueryParam("nameLike")
    private String nameLike;
    /** \u5f53\u524d\u5546\u54c1\u3001\u8d26\u53f7\u6216\u6743\u9650\u8bb0\u5f55\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
    @QueryParam("status")
    private String status;
    /** \u7b5b\u9009\u5546\u54c1\u65f6\u7684\u4ef7\u683c\u4e0b\u9650\u3002 */
    @QueryParam("priceMin")
    private BigDecimal priceMin;
    /** \u7b5b\u9009\u5546\u54c1\u65f6\u7684\u4ef7\u683c\u4e0a\u9650\u3002 */
    @QueryParam("priceMax")
    private BigDecimal priceMax;
    /** \u53ef\u89e3\u6790\u4e3a\u6392\u5e8f\u5b57\u6bb5\u548c\u65b9\u5411\u7684\u67e5\u8be2\u6587\u672c\u3002 */
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
     * \u628a\u63d0\u4f9b\u7684\u67e5\u8be2\u72b6\u6001\u8f6c\u6362\u4e3a\u5546\u54c1\u72b6\u6001\u679a\u4e3e\uff0c\u7a7a\u767d\u503c\u4e0d\u9650\u5236\u72b6\u6001\u3002
     * @return \u89e3\u6790\u5230\u7684\u5546\u54c1\u72b6\u6001\uff0c\u6ca1\u6709\u503c\u65f6\u4e3a\u7a7a
     */
    private GoodsStatus parseStatus() {
        return StrKit.isBlank(status) ? null : GoodsStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }
}
