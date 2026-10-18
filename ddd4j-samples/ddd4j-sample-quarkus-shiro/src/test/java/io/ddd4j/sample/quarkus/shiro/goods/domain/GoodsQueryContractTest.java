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
package io.ddd4j.sample.quarkus.shiro.goods.domain;

import io.ddd4j.core.cqrs.query.LambdaCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 商品排序解析在三线上的共同契约。 */
class GoodsQueryContractTest {

    @Test
    void supportsAllWhitelistedSortPropertiesAndIgnoresInvalidClauses() {
        GoodsQuery query = new GoodsQuery();

        assertSame(query, query.setOrderBys(
                "id_ASC,createTime_DESC,updateTime_ASC,price_DESC,unknown_ASC,invalid"));

        List<LambdaCondition> orders = query.getOrderByConditions();
        assertEquals(4, orders.size());
        assertOrder(orders.get(0), "id", "ASC");
        assertOrder(orders.get(1), "createTime", "DESC");
        assertOrder(orders.get(2), "updateTime", "ASC");
        assertOrder(orders.get(3), "price", "DESC");
    }

    @Test
    void blankSortExpressionDoesNotAddConditions() {
        GoodsQuery query = new GoodsQuery();

        assertSame(query, query.setOrderBys("  "));
        assertEquals(0, query.getOrderByConditions().size());
    }

    private static void assertOrder(LambdaCondition condition, String property, String direction) {
        assertEquals(property, condition.property());
        assertEquals(direction, condition.getOperator());
    }
}
