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
import javax.ws.rs.Produces;
import javax.ws.rs.PathParam;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.GET;
import javax.ws.rs.Consumes;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.Data;
import lombok.NoArgsConstructor;

import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsApplication;
import io.ddd4j.sample.dropwizard.cqrs.command.CreateOrderCommand;
import io.ddd4j.sample.dropwizard.cqrs.readmodel.OrderSummaryViewEntity;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 订单 REST 资源（Dropwizard 运行时）。
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static Map<String, Object> response(boolean success, String key, Object value) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put(Objects.requireNonNull(key, "key must not be null"),
                Objects.requireNonNull(value, "value must not be null"));
        return Collections.unmodifiableMap(response);
    }

    /**
     * 接收创建订单请求并分发命令。
     *
     * @param request 创建订单请求
     * @return 创建结果
     */
    @POST
    public Response createOrder(CreateOrderRequest request) {
        if (DropwizardCqrsApplication.ORDER_REPO.findByOrderNo(request.orderNo()).isPresent()) {
            return Response.status(409)
                    .entity(response(false, "message", "order already exists: " + request.orderNo()))
                    .build();
        }

        CreateOrderCommand command = new CreateOrderCommand(
                request.orderNo(), request.buyerId(), request.buyerName());
        Result<String> result = DropwizardCqrsApplication.COMMAND_BUS.execute(command);

        return Response.status(201)
                .entity(response(true, "orderId", result.getData()))
                .build();
    }

    /**
     * 读取订单。
     *
     * @param id 订单标识
     * @return 查询结果
     */
    @GET
    @Path("/{id}")
    public Response getOrder(@PathParam("id") String id) {
        OrderSummaryViewEntity entity = DropwizardCqrsApplication.READ_VIEW.findById(id);
        if (entity == null) {
            return Response.status(404).build();
        }
        return Response.ok(entity).build();
    }

    /**
     * 订单创建请求数据。
     */
    @Data
    @NoArgsConstructor
    public static class CreateOrderRequest {

        private static final long serialVersionUID = 0L;

        private String orderNo;

        private String buyerId;

        private String buyerName;

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

    }
}
