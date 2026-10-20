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

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/** 商品查询 HTTP 路由在三条平台线的共同契约。 */
@QuarkusTest
class GoodsQueryResourceContractTest {

    @Test
    void emptyCatalogHasZeroCount() {
        given().when().get("/api/goods/count")
                .then().statusCode(200).body("data", equalTo(0));
    }

    @Test
    void emptyCatalogHasEmptyList() {
        given().when().get("/api/goods/list")
                .then().statusCode(200).body("data", hasSize(0));
    }
}
