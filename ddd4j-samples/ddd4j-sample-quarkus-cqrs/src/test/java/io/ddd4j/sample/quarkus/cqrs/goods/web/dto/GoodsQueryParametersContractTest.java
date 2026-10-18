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
package io.ddd4j.sample.quarkus.cqrs.goods.web.dto;

import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 三线共同的商品 HTTP 查询参数映射契约。 */
class GoodsQueryParametersContractTest {

    @Test
    void mapsFiltersAndOrderedFields() {
        GoodsQueryParameters parameters = new GoodsQueryParameters();
        parameters.setCode("SKU-1");
        parameters.setStatus("on_sale");
        parameters.setOrderBys("name_DESC,id_ASC");

        GoodsQuery query = parameters.toQuery();
        assertEquals("SKU-1", query.getCode());
        assertEquals(GoodsStatus.ON_SALE, query.getStatus());
        List<LambdaCondition> orders = query.getOrderByConditions();
        assertEquals(2, orders.size());
        assertEquals("name", orders.get(0).property());
        assertEquals("DESC", orders.get(0).operator());
        assertEquals("id", orders.get(1).property());
        assertEquals("ASC", orders.get(1).operator());
    }

    @Test
    void rejectsUnsupportedSortField() {
        GoodsQueryParameters parameters = new GoodsQueryParameters();
        parameters.setOrderBys("unknown_DESC");
        assertThrows(IllegalArgumentException.class, parameters::toQuery);
    }
}
