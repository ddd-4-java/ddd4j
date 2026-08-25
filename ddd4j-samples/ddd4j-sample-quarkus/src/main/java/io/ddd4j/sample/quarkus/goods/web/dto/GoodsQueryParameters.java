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

/** HTTP query parameters translated into the framework-independent goods query. */
@Getter
@Setter
public class GoodsQueryParameters {

    @QueryParam("current")
    private Long current;
    @QueryParam("size")
    private Long size;
    @QueryParam("code")
    private String code;
    @QueryParam("nameLike")
    private String nameLike;
    @QueryParam("status")
    private String status;
    @QueryParam("priceMin")
    private BigDecimal priceMin;
    @QueryParam("priceMax")
    private BigDecimal priceMax;
    @QueryParam("orderBys")
    private String orderBys;

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

    private GoodsStatus parseStatus() {
        return StrKit.isBlank(status) ? null : GoodsStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }
}
