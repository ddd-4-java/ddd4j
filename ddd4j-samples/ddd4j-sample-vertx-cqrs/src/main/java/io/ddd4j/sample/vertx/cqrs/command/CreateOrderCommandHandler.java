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
package io.ddd4j.sample.vertx.cqrs.command;

import io.ddd4j.sample.vertx.cqrs.repository.EventSourcingOrderRepository;
import io.ddd4j.sample.order.domain.Order;


import java.util.Objects;

/**
 * 创建订单命令处理器（写侧入口）。
 *
 * <p>用 {@link Order#draft} 工厂方法创建聚合根，
 * 通过 {@link EventSourcingOrderRepository} 持久化到 EventStore。
 */

public class CreateOrderCommandHandler {

    private final EventSourcingOrderRepository orderRepository;

    public CreateOrderCommandHandler(EventSourcingOrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    public String execute(CreateOrderCommand command) {
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
        orderRepository.save(order);
        return order.id();
    }
}
