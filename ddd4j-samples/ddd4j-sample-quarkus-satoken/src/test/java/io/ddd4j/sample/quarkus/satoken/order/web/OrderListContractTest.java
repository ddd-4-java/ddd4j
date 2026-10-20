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
package io.ddd4j.sample.quarkus.satoken.order.web;

import io.ddd4j.sample.quarkus.satoken.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.satoken.order.domain.service.OrderDomainService;
import io.ddd4j.sample.quarkus.satoken.order.infrastructure.InMemoryOrderRepository;
import io.ddd4j.sample.quarkus.satoken.order.web.dto.OrderResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 订单列表返回值的三线行为契约。 */
class OrderListContractTest {

    @Test
    void listResultRemainsMutable() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        OrderApplicationService service = new OrderApplicationService(repository, new OrderDomainService(repository));
        List<OrderResponse> data = new OrderResource(service).listAll().getData();
        assertEquals(0, data.size());
        data.add(null);
        assertEquals(1, data.size());
    }
}
