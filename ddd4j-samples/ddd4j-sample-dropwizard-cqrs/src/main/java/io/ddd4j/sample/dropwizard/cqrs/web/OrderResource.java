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

import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsApplication;
import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.dropwizard.cqrs.readmodel.OrderSummaryViewEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
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
        if (DropwizardCqrsApplication.ORDER_REPO.findByOrderNo(request.getOrderNo()).isPresent()) {
            return Response.status(409)
                    .entity(response(false, "message", "order already exists: " + request.getOrderNo()))
                    .build();
        }

        CreateOrderCommand command = new CreateOrderCommand(
                request.getOrderNo(), request.getBuyerId(), request.getBuyerName());
        Result<String> result = DropwizardCqrsApplication.COMMAND_BUS.execute(command);

        return Response.status(201)
                .entity(response(true, "orderId", result.getData()))
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

    private static Map<String, Object> response(boolean success, String key, Object value) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put(key, value);
        return response;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {
        private String orderNo;
        private String buyerId;
        private String buyerName;
    }
}
