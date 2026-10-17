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
package io.ddd4j.sample.javalin.satoken.order.domain.model;

import io.ddd4j.sample.javalin.satoken.order.application.AddOrderLineCommand;
import io.ddd4j.sample.javalin.satoken.order.application.CreateOrderCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 锁定三线订单值对象的哈希契约，覆盖手写与 Lombok 实现。
 */
class ValueHashContractTest {

    @Test
    void moneyUsesTheSharedValueHash() {
        Money money = new Money(new BigDecimal("12.34"), " cny ");
        assertEquals(valueHash(money.getAmount(), money.getCurrency()), money.hashCode());
    }

    @Test
    void commandsUseTheSharedValueHashIncludingNullFields() {
        CreateOrderCommand create = new CreateOrderCommand("ORDER-1", null, "Ada");
        assertEquals(valueHash(create.getOrderNo(), create.getBuyerId(), create.getBuyerName()),
                create.hashCode());

        AddOrderLineCommand add = new AddOrderLineCommand("ORDER-1", "GOODS-1", null, 2, null);
        assertEquals(valueHash(add.getQuantity(), add.getOrderId(), add.getGoodsId(),
                add.getGoodsName(), add.getUnitPrice()), add.hashCode());
    }

    private static int valueHash(Object... values) {
        int result = 1;
        for (Object value : values) {
            result = 59 * result + (Objects.isNull(value) ? 43 : value.hashCode());
        }
        return result;
    }
}
