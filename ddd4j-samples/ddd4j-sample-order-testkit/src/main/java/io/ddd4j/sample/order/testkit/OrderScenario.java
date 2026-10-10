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

import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;

import java.math.BigDecimal;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class OrderScenario {

    private final InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
    private final OrderApplicationService application = new OrderApplicationService(
            adapters, adapters, adapters, adapters, adapters);

    /**
     * paidOrder() 方法。
     * @param orderNo 订单号。
     * @return 方法执行结果。
     */
    public Order paidOrder(String orderNo) {
        Order order = application.create(new CreateOrderCommand(orderNo, "buyer-1", "Alice"));
        application.addLine(new AddOrderLineCommand(order.id(), "goods-1", "DDD Book", 2,
                new BigDecimal("59.90")));
        return application.pay(order.id(), "payment-" + orderNo);
    }

    /**
     * application() 方法。
     * @return 方法执行结果。
     */
    public OrderApplicationService application() {
        return application;
    }

    /**
     * adapters() 方法。
     * @return 方法执行结果。
     */
    public InMemoryOrderAdapters adapters() {
        return adapters;
    }
}
