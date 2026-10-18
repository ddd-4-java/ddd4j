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
package io.ddd4j.sample.javalin.cqrs.goods.domain;

import io.ddd4j.core.cqrs.query.LambdaCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 商品查询排序的三线契约。 */
class GoodsQueryOrderContractTest {

    @Test
    void recognizesAllowedFieldsAndDirections() {
        GoodsQuery query = new GoodsQuery();

        assertSame(query, query.setOrderBys("id_ASC,price_DESC,unknown_ASC"));
        List<LambdaCondition> order = query.getOrderByConditions();
        assertEquals(2, order.size());
        assertEquals("ASC", order.get(0).getOperator());
        assertEquals("DESC", order.get(1).getOperator());
    }
}
