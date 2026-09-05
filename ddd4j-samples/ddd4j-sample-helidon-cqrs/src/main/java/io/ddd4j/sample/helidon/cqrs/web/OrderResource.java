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

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.helidon.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.helidon.cqrs.readmodel.OrderSummaryViewEntity;
import io.ddd4j.sample.helidon.cqrs.repository.EventSourcingOrderRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

    @Inject
    public OrderResource(EventSourcingOrderRepository orderRepository,
                         CommandBus commandBus,
                         OrderSummaryView readView) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus must not be null");
        this.readView = Objects.requireNonNull(readView, "readView must not be null");
    }

    @POST
    public Response createOrder(CreateOrderRequest request) {
        if (orderRepository.findByOrderNo(request.orderNo()).isPresent()) {
            return Response.status(409)
                    .entity(Map.of("success", false, "message", "order already exists: " + request.orderNo()))
                    .build();
        }

        CreateOrderCommand command = new CreateOrderCommand(
                request.orderNo(), request.buyerId(), request.buyerName());
        Result<String> result = commandBus.execute(command);

        return Response.status(201)
                .entity(Map.of("success", true, "orderId", result.getData()))
                .build();
    }

    @GET
    @Path("/{id}")
    public Response getOrder(@PathParam("id") String id) {
        OrderSummaryViewEntity entity = readView.findById(id);
        if (entity == null) {
            return Response.status(404).build();
        }
        return Response.ok(entity).build();
    }public final class CreateOrderRequest {
        private final String orderNo;
        private final String buyerId;
        private final String buyerName;

        public CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }
        public String orderNo() { return orderNo; }
        public String buyerId() { return buyerId; }
        public String buyerName() { return buyerName; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateOrderRequest other = (CreateOrderRequest) o;
            return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderNo, buyerId, buyerName); }
        @Override
        public String toString() {
            return "CreateOrderRequest{" + "orderNo=" + orderNo + ", " + "buyerId=" + buyerId + ", " + "buyerName=" + buyerName + "}";
        }
    
    }
}
