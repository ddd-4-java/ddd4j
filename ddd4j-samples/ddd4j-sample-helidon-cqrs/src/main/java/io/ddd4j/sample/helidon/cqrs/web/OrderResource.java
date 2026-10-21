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
package io.ddd4j.sample.helidon.cqrs.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryViewEntity;
import io.ddd4j.sample.helidon.cqrs.repository.EventSourcingOrderRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 订单 REST 资源（Helidon MP 运行时）。
 *
 * <p>通过 CDI {@code @Inject} 获取 core CQRS 组件。
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OrderResource {

    private final EventSourcingOrderRepository orderRepository;
    private final CommandBus commandBus;
    private final OrderSummaryView readView;

    /**
     * 创建订单资源。
     *
     * @param orderRepository 事件溯源订单仓储
     * @param commandBus 命令总线
     * @param readView 订单摘要视图
     */
    @Inject
    public OrderResource(EventSourcingOrderRepository orderRepository,
                         CommandBus commandBus,
                         OrderSummaryView readView) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus must not be null");
        this.readView = Objects.requireNonNull(readView, "readView must not be null");
    }

    /**
     * 接收创建订单请求并分发命令。
     *
     * @param request 创建订单请求
     * @return 创建结果
     */
    @POST
    public Response createOrder(CreateOrderRequest request) {
        if (orderRepository.findByOrderNo(request.orderNo()).isPresent()) {
            return Response.status(409)
                    .entity(responseBody(false, "message", "order already exists: " + request.orderNo()))
                    .build();
        }

        CreateOrderCommand command = new CreateOrderCommand(
                request.orderNo(), request.buyerId(), request.buyerName());
        Result<String> result = commandBus.execute(command);

        return Response.status(201)
                .entity(responseBody(true, "orderId", result.getData()))
                .build();
    }

    /**
     * 读取订单。
     *
     * @param id 订单标识
     * @return 查询结果
     */
    private static Map<String, Object> responseBody(boolean success, String key, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
        return Collections.unmodifiableMap(body);
    }

    @GET
    @Path("/{id}")
    public Response getOrder(@PathParam("id") String id) {
        OrderSummaryViewEntity entity = readView.findById(id);
        if (entity == null) {
            return Response.status(404).build();
        }
        return Response.ok(entity).build();
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
        @JsonbCreator
        public CreateOrderRequest(@JsonProperty("orderNo") @JsonbProperty("orderNo") String orderNo,
                                  @JsonProperty("buyerId") @JsonbProperty("buyerId") String buyerId,
                                  @JsonProperty("buyerName") @JsonbProperty("buyerName") String buyerName) {
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
