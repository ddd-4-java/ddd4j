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
package io.ddd4j.sample.dropwizard.cqrs.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsApplication;
import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.dropwizard.cqrs.readmodel.OrderSummaryViewEntity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * 订单 REST 资源（Dropwizard 运行时）。
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @POST
    public Response createOrder(CreateOrderRequest request) {
        if (DropwizardCqrsApplication.ORDER_REPO.findByOrderNo(request.orderNo()).isPresent()) {
            return Response.status(409)
                    .entity(Map.of("success", false, "message", "order already exists: " + request.orderNo()))
                    .build();
        }

        CreateOrderCommand command = new CreateOrderCommand(
                request.orderNo(), request.buyerId(), request.buyerName());
        Result<String> result = DropwizardCqrsApplication.COMMAND_BUS.execute(command);

        return Response.status(201)
                .entity(Map.of("success", true, "orderId", result.getData()))
                .build();
    }

    @GET
    @Path("/{id}")
    public Response getOrder(@PathParam("id") String id) {
        OrderSummaryViewEntity entity = DropwizardCqrsApplication.READ_VIEW.findById(id);
        if (entity == null) {
            return Response.status(404).build();
        }
        return Response.ok(entity).build();
    }

    public final static class CreateOrderRequest {

        private static final long serialVersionUID = 0L;

        private final String orderNo;

        private final String buyerId;

        private final String buyerName;

        @JsonCreator()
        public CreateOrderRequest(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }

        @JsonProperty("orderNo")
        public String orderNo() {
            return orderNo;
        }

        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

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
