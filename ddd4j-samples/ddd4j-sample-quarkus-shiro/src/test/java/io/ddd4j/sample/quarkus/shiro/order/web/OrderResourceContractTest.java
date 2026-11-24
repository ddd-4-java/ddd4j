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
package io.ddd4j.sample.quarkus.shiro.order.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;

/** 三线订单列表 HTTP 入口的共同契约。 */
@QuarkusTest
@TestProfile(OrderResourceContractTest.EmptyStoreProfile.class)
class OrderResourceContractTest {

    /** 独立启动保持本用例原有空仓储前提，不清理其他用例的业务数据。 */
    public static class EmptyStoreProfile implements QuarkusTestProfile {
    }

    @Test
    void emptyOrderListIsAvailable() {
        given().when().get("/orders")
                .then().statusCode(200).body("data", hasSize(0));
    }
}
