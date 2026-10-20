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
package io.ddd4j.sample.quarkus.cqrs.order.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.quarkus.cqrs.order.domain.repository.OrderRepository;
import io.ddd4j.sample.quarkus.cqrs.order.infrastructure.InMemoryOrderRepository;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 基础查询端列表结果的三线行为契约。 */
class OrderQueryListContractTest {

    @Test
    void nonMemoryRepositoryReturnsImmutableEmptyListAndZeroCount() {
        OrderRepository repository = (OrderRepository) Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(), new Class<?>[]{OrderRepository.class},
                (proxy, method, args) -> null);
        OrderQueryResource resource = new OrderQueryResource(null, repository);
        @SuppressWarnings("unchecked")
        List<Object> data = (List<Object>) ((R<?>) resource.list(null).getEntity()).getData();
        assertEquals(0, data.size());
        assertThrows(UnsupportedOperationException.class, () -> data.add(null));
        assertEquals(0L, ((R<?>) resource.count().getEntity()).getData());
    }

    @Test
    void inMemoryResultRemainsMutable() {
        Response response = new OrderQueryResource(null, new InMemoryOrderRepository()).list(null);
        @SuppressWarnings("unchecked")
        List<Object> data = (List<Object>) ((R<?>) response.getEntity()).getData();
        assertEquals(0, data.size());
        data.add(null);
        assertEquals(1, data.size());
    }
}
