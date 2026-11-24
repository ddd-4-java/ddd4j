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
package io.ddd4j.sample.javalin.cqrs;

import com.fasterxml.jackson.databind.JsonNode;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 验证真实 Javalin HTTP 入口在订单写入后刷新旧读侧缓存。 */
class LegacyOrderCacheHttpTest {

    private static final String ORDERS = "/api/orders";
    private static final String QUERY = ORDERS + "/query";

    @Test
    void successfulWritesRefreshWarmOrderDetailAndStatusTotals() {
        try (JavalinCqrsApplication.Application application = JavalinCqrsApplication.start(0, null)) {
            EnvelopeOrderHttpDriver http = driver(application);
            String id = http.create(new CreateOrderCommand("cache-order", "buyer", "Alice"));
            assertFalse(id.isEmpty());
            String detail = QUERY + "/detail/" + id;
            assertEquals("DRAFT", get(http, detail).path("status").asText());
            assertEquals(1, get(http, QUERY + "/stats").path("byStatus").path("DRAFT").asInt());

            http.addLine(new AddOrderLineCommand(id, "book", "Book", 2, new BigDecimal("25.00")));
            JsonNode afterLine = get(http, detail);
            http.pay(id, "legacy-payment");
            JsonNode afterPay = get(http, detail);
            JsonNode paidStats = get(http, QUERY + "/stats").path("byStatus");
            http.request("POST", ORDERS + "/" + id + "/ship", null, Collections.emptyMap());
            JsonNode afterShip = get(http, detail);
            JsonNode shippedStats = get(http, QUERY + "/stats").path("byStatus");

            String cancelledId = http.create(new CreateOrderCommand("cache-cancel", "buyer", "Alice"));
            String cancelledDetail = QUERY + "/detail/" + cancelledId;
            assertEquals("DRAFT", get(http, cancelledDetail).path("status").asText());
            get(http, QUERY + "/stats");
            http.request("POST", ORDERS + "/" + cancelledId + "/cancel", null, Collections.emptyMap());
            JsonNode afterCancel = get(http, cancelledDetail);
            JsonNode cancelledStats = get(http, QUERY + "/stats").path("byStatus");

            assertAll(
                    () -> assertEquals(1, afterLine.path("lineCount").asInt(), "订单行缓存未刷新"),
                    () -> assertEquals(0, new BigDecimal("50.00").compareTo(
                            afterLine.path("totalAmount").path("amount").decimalValue())),
                    () -> assertEquals("PAID", afterPay.path("status").asText(), "支付后仍读到旧状态"),
                    () -> assertEquals(1, paidStats.path("PAID").asInt(), "支付后统计未刷新"),
                    () -> assertEquals("SHIPPED", afterShip.path("status").asText(), "发货后仍读到旧状态"),
                    () -> assertEquals(1, shippedStats.path("SHIPPED").asInt(), "发货后统计未刷新"),
                    () -> assertEquals("CANCELLED", afterCancel.path("status").asText(), "取消后仍读到旧状态"),
                    () -> assertEquals(1, cancelledStats.path("CANCELLED").asInt(), "取消后统计未刷新")
            );
        }
    }

    @Test
    void creationRefreshesWarmBuyerCountAndOrderTotal() {
        try (JavalinCqrsApplication.Application application = JavalinCqrsApplication.start(0, null)) {
            EnvelopeOrderHttpDriver http = driver(application);
            String alice = QUERY + "/buyer/alice/count";
            String bob = QUERY + "/buyer/bob/count";
            assertEquals(0, get(http, alice).path("count").asInt());
            assertEquals(0, get(http, bob).path("count").asInt());
            assertEquals(0, get(http, QUERY + "/stats").path("total").asInt());

            assertFalse(http.create(new CreateOrderCommand("cache-buyer", "alice", "Alice")).isEmpty());

            assertAll(
                    () -> assertEquals(1, get(http, alice).path("count").asInt(), "创建后买家计数未刷新"),
                    () -> assertEquals(0, get(http, bob).path("count").asInt()),
                    () -> assertEquals(1, get(http, QUERY + "/stats").path("total").asInt(), "创建后总数未刷新")
            );
        }
    }

    private EnvelopeOrderHttpDriver driver(JavalinCqrsApplication.Application application) {
        return new EnvelopeOrderHttpDriver("http://127.0.0.1:" + application.app().port(),
                ORDERS, Collections.emptyMap());
    }

    private JsonNode get(EnvelopeOrderHttpDriver driver, String path) {
        return driver.request("GET", path, null, Collections.emptyMap());
    }
}
