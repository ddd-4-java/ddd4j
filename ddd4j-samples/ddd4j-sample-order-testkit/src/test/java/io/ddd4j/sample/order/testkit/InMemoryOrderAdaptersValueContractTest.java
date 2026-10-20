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
package io.ddd4j.sample.order.testkit;

import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证本地订单适配器返回的列表在三条 Java 兼容线中均不可修改。
 */
class InMemoryOrderAdaptersValueContractTest {

    @Test
    void returnsUnmodifiableOrderAndOutboxLists() {
        OrderScenario scenario = new OrderScenario();
        scenario.paidOrder("ORDER-LIST-CONTRACT");
        InMemoryOrderAdapters adapters = scenario.adapters();

        assertFalse(adapters.findAll(0, 10).isEmpty());
        assertFalse(adapters.pending(10).isEmpty());
        assertFalse(adapters.query(new OrderQuery(null, null, 1, 10)).isEmpty());

        assertThrows(UnsupportedOperationException.class, () -> adapters.findAll(0, 10).add(null));
        assertThrows(UnsupportedOperationException.class, () -> adapters.pending(10).add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> adapters.query(new OrderQuery(null, null, 1, 10)).add(null));
    }
}
