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
package io.ddd4j.sample.quarkus.satoken.order.web.dto;

import io.ddd4j.sample.quarkus.satoken.order.domain.model.Money;
import io.ddd4j.sample.quarkus.satoken.order.domain.model.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 三线相同的订单响应列表快照契约。 */
class OrderResponseSnapshotContractTest {

    @Test
    void linesAreImmutableSnapshotOfOrderAtConversionTime() {
        Order order = Order.draft("ORD-1", "BUYER-1", "buyer");
        order.addLine("SKU-1", "first", 1, Money.cny("12.50"));
        OrderResponse response = OrderResponse.from(order);

        assertEquals(1, response.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> response.lines().clear());
        order.addLine("SKU-2", "second", 1, Money.cny("2.00"));
        assertEquals(1, response.lines().size());
    }
}
