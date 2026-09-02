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
package io.ddd4j.sample.micronaut.cqrs.web;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.micronaut.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.micronaut.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.micronaut.cqrs.readmodel.OrderSummaryViewEntity;
import io.ddd4j.sample.micronaut.cqrs.repository.EventSourcingOrderRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;

import java.util.Map;
import java.util.Objects;

/**
 * 订单 REST 控制器（Micronaut 运行时）。
 *
 * <ul>
 *   <li>{@code POST /orders} -> 创建订单（写侧）</li>
 *   <li>{@code GET /orders/{id}} -> 查询订单摘要（读侧）</li>
 * </ul>
 */
@Controller("/orders")
public class OrderController {

    private final CommandBus commandBus;
    private final EventSourcingOrderRepository orderRepository;
    private final OrderSummaryView readView;

    public OrderController(CommandBus commandBus,
                           EventSourcingOrderRepository orderRepository,
                           OrderSummaryView readView) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.readView = Objects.requireNonNull(readView, "readView must not be null");
    }

    @Post(produces = MediaType.APPLICATION_JSON)
    @Status(io.micronaut.http.HttpStatus.CREATED)
    public HttpResponse<Map<String, Object>> createOrder(@Body CreateOrderRequest request) {
        if (orderRepository.findByOrderNo(request.orderNo()).isPresent()) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", "order already exists: " + request.orderNo()));
        }

        CreateOrderCommand command = new CreateOrderCommand(
                request.orderNo(), request.buyerId(), request.buyerName());
        Result<String> result = commandBus.execute(command);

        return HttpResponse.status(io.micronaut.http.HttpStatus.CREATED)
                .body(Map.of("success", true, "orderId", result.getData()));
    }

    @Get("/{id}")
    public HttpResponse<OrderSummaryViewEntity> getOrder(@PathVariable String id) {
        OrderSummaryViewEntity entity = readView.findById(id);
        if (entity == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(entity);
    }

    public record CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
    }
}
