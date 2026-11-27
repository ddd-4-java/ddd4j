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

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商品查询 HTTP 路由在三条平台线的共同契约。
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(io.ddd4j.sample.quarkus.shiro.GoodsStandaloneProfile.class)
class GoodsQueryResourceContractTest {

    /** \u9a8c\u8bc1<code>Arc.container().instance(GoodsResource.class) .getBean().getScope().getSimpleName()</code> \u7b49\u4e8e <code>"ApplicationScoped"</code>\u3002 */
    @Test
    void goodsResourceUsesApplicationScope() {
        assertEquals("ApplicationScoped", Arc.container().instance(GoodsResource.class)
                .getBean().getScope().getSimpleName());
    }

    /** \u8bf7\u6c42 <code>GET /api/goods/count</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\uff0c\u54cd\u5e94\u4e2d\u7684 <code>data</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void emptyCatalogHasZeroCount() {
        given().when().get("/api/goods/count")
                .then().statusCode(200).body("data", equalTo(0));
    }

    /** \u8bf7\u6c42 <code>GET /api/goods/list</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002 */
    @Test
    void emptyCatalogHasEmptyList() {
        given().when().get("/api/goods/list")
                .then().statusCode(200).body("data", hasSize(0));
    }
}
