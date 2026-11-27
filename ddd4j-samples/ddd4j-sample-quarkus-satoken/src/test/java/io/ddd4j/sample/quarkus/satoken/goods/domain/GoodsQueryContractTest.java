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
package io.ddd4j.sample.quarkus.satoken.goods.domain;

import io.ddd4j.core.cqrs.query.LambdaCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 商品排序解析在三线上的共同契约。
 */
class GoodsQueryContractTest {

    /**
     * \u6838\u5bf9<code>condition.property()</code> \u7b49\u4e8e <code>property</code>\uff0c<code>condition.getOperator()</code> \u7b49\u4e8e <code>direction</code>\u3002
     * @param condition \u51b3\u5b9a\u672c\u6b21\u6d4b\u8bd5\u64cd\u4f5c\u6216\u65ad\u8a00\u662f\u5426\u6210\u7acb\u7684\u6761\u4ef6
     * @param property \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param direction \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     */
    private static void assertOrder(LambdaCondition condition, String property, String direction) {
        assertEquals(property, condition.property());
        assertEquals(direction, condition.getOperator());
    }

    /** \u9a8c\u8bc1<code>query.setOrderBys( "id_ASC,createTime_DESC,updateTime_ASC,price_DESC,unknown_ASC,invalid")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>query</code>\uff0c<code>orders.size()</code> \u7b49\u4e8e <code>4</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>query.setOrderBys(" ")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>query</code>\uff0c<code>query.getOrderByConditions().size()</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void blankSortExpressionDoesNotAddConditions() {
        GoodsQuery query = new GoodsQuery();

        assertSame(query, query.setOrderBys("  "));
        assertEquals(0, query.getOrderByConditions().size());
    }
}
