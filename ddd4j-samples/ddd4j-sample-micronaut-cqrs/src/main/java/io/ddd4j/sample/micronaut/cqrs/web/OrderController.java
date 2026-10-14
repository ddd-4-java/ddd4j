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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

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

    /**
     * 创建订单控制器。
     *
     * @param commandBus 命令总线
     * @param orderRepository 事件溯源订单仓储
     * @param readView 订单摘要视图
     */
    public OrderController(CommandBus commandBus,
                           EventSourcingOrderRepository orderRepository,
                           OrderSummaryView readView) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.readView = Objects.requireNonNull(readView, "readView must not be null");
    }

    /**
     * 接收创建订单请求并分发命令。
     *
     * @param request 创建订单请求
     * @return 创建结果
     */
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

    /**
     * 读取订单。
     *
     * @param id 订单标识
     * @return 查询结果
     */
    @Get("/{id}")
    public HttpResponse<OrderSummaryViewEntity> getOrder(@PathVariable String id) {
        OrderSummaryViewEntity entity = readView.findById(id);
        if (entity == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(entity);
    }

    /**
     * 订单创建请求数据。
     */
    public final static class CreateOrderRequest {

        private static final long serialVersionUID = 0L;

        private final String orderNo;

        private final String buyerId;

        private final String buyerName;

        /**
         * 创建订单创建请求。
         *
         * @param orderNo 订单编号
         * @param buyerId 买家标识
         * @param buyerName 买家名称
         */
        @JsonCreator()
        public CreateOrderRequest(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }

        /**
         * 读取订单编号。
         *
         * @return 订单编号
         */
        @JsonProperty("orderNo")
        public String orderNo() {
            return orderNo;
        }

        /**
         * 读取买家标识。
         *
         * @return 买家标识
         */
        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

        /**
         * 读取买家名称。
         *
         * @return 买家名称
         */
        @JsonProperty("buyerName")
        public String buyerName() {
            return buyerName;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            CreateOrderRequest other = (CreateOrderRequest) obj;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(orderNo);
            result = 31 * result + Objects.hashCode(buyerId);
            result = 31 * result + Objects.hashCode(buyerName);
            return result;
        }

        @Override
        public String toString() {
            return "CreateOrderRequest[orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + "]";
        }
    }
}
