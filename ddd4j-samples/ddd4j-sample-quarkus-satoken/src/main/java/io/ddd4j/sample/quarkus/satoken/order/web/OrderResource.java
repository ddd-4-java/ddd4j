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
package io.ddd4j.sample.quarkus.satoken.order.web;

import jakarta.enterprise.context.ApplicationScoped;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.quarkus.satoken.order.application.AddOrderLineCommand;
import io.ddd4j.sample.quarkus.satoken.order.application.CreateOrderCommand;
import io.ddd4j.sample.quarkus.satoken.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.satoken.order.domain.model.Money;
import io.ddd4j.sample.quarkus.satoken.order.domain.model.Order;
import io.ddd4j.sample.quarkus.satoken.order.web.dto.AddOrderLineRequest;
import io.ddd4j.sample.quarkus.satoken.order.web.dto.CreateOrderRequest;
import io.ddd4j.sample.quarkus.satoken.order.web.dto.OrderResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单 JAX-RS 资源（写侧）。
 *
 * <p>Quarkus 下使用 {@code @Path} / {@code @GET} / {@code @POST} 注解，
 * 通过 {@code @Inject} 注入 {@link OrderApplicationService}。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>POST /orders                    创建订单</li>
 *   <li>GET  /orders                    列表查询</li>
 *   <li>GET  /orders/{id}              查询订单</li>
 *   <li>GET  /orders/by-order-no       按订单号查询</li>
 *   <li>POST /orders/{id}/lines        添加订单行</li>
 *   <li>POST /orders/{id}/pay          支付订单</li>
 *   <li>POST /orders/{id}/ship         发货订单</li>
 *   <li>POST /orders/{id}/cancel       取消订单</li>
 *   <li>GET  /orders/{id}/discount     预览折扣</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OrderResource {

    private final OrderApplicationService orderApplicationService;

    /**
     * 创建订单资源。
     *
     * @param orderApplicationService 订单应用服务
     */
    @Inject
    public OrderResource(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * 接收创建订单请求。
     *
     * @param request 请求数据
     * @return 创建的订单响应
     */
    @POST
    public R<OrderResponse> create(CreateOrderRequest request) {
        Order order = orderApplicationService.createDraft(
                new CreateOrderCommand(request.orderNo(), request.buyerId(), request.buyerName())
        );
        return R.ok("order created", OrderResponse.from(order));
    }

    /**
     * 按标识查询订单。
     *
     * @param id 订单标识
     * @return 订单
     */
    @GET
    @Path("/{id}")
    public R<OrderResponse> findById(@PathParam("id") String id) {
        Order order = orderApplicationService.findById(id);
        return R.ok(OrderResponse.from(order));
    }

    /**
     * 按编号查询订单。
     *
     * @param orderNo 订单编号
     * @return 订单响应
     */
    @GET
    @Path("/by-order-no")
    public R<OrderResponse> findByOrderNo(@QueryParam("orderNo") String orderNo) {
        Order order = orderApplicationService.findByOrderNo(orderNo);
        return R.ok(OrderResponse.from(order));
    }

    /**
     * 列出全部订单。
     *
     * @return 订单响应列表
     */
    @GET
    public R<List<OrderResponse>> listAll() {
        List<OrderResponse> items = orderApplicationService.listAll().stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
        return R.ok(items);
    }

    /**
     * 向订单添加商品行。
     *
     * @param id 订单标识
     * @param request 请求数据
     * @return 更新后的订单响应
     */
    @POST
    @Path("/{id}/lines")
    public R<OrderResponse> addLine(@PathParam("id") String id, AddOrderLineRequest request) {
        Order order = orderApplicationService.addLine(new AddOrderLineCommand(
                id, request.goodsId(), request.goodsName(), request.quantity(), request.unitPrice()
        ));
        return R.ok("order line added", OrderResponse.from(order));
    }

    /**
     * 支付订单。
     *
     * @param id 订单标识
     * @return 更新后的订单响应
     */
    @POST
    @Path("/{id}/pay")
    public R<OrderResponse> pay(@PathParam("id") String id) {
        Order order = orderApplicationService.pay(id);
        return R.ok("order paid", OrderResponse.from(order));
    }

    /**
     * 发货订单。
     *
     * @param id 订单标识
     * @return 更新后的订单响应
     */
    @POST
    @Path("/{id}/ship")
    public R<OrderResponse> ship(@PathParam("id") String id) {
        Order order = orderApplicationService.ship(id);
        return R.ok("order shipped", OrderResponse.from(order));
    }

    /**
     * 取消订单。
     *
     * @param id 订单标识
     * @return 更新后的订单响应
     */
    @POST
    @Path("/{id}/cancel")
    public R<OrderResponse> cancel(@PathParam("id") String id) {
        Order order = orderApplicationService.cancel(id);
        return R.ok("order cancelled", OrderResponse.from(order));
    }

    /**
     * 预览订单折扣。
     *
     * @param id 订单标识
     * @return 折扣金额响应
     */
    @GET
    @Path("/{id}/discount")
    public R<DiscountView> previewDiscount(@PathParam("id") String id) {
        Money discounted = orderApplicationService.previewDiscount(id);
        return R.ok(new DiscountView(discounted.amount().toPlainString(), discounted.currency()));
    }

    /**
     * 折扣预览响应。
     */
    public final static class DiscountView {

        private static final long serialVersionUID = 0L;

        private final String amount;

        private final String currency;

        /**
         * 创建折扣金额视图。
         *
         * @param amount 金额数值
         * @param currency 币种
         */
        @JsonCreator()
        public DiscountView(@JsonProperty("amount") String amount, @JsonProperty("currency") String currency) {
            this.amount = amount;
            this.currency = currency;
        }

        /**
         * 读取金额数值。
         *
         * @return 金额数值
         */
        @JsonProperty("amount")
        public String amount() {
            return amount;
        }

        /**
         * 读取币种。
         *
         * @return 币种
         */
        @JsonProperty("currency")
        public String currency() {
            return currency;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            DiscountView other = (DiscountView) obj;
            return Objects.equals(this.amount, other.amount) && Objects.equals(this.currency, other.currency);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(amount);
            result = 31 * result + Objects.hashCode(currency);
            return result;
        }

        @Override
        public String toString() {
            return "DiscountView[amount=" + amount + ", currency=" + currency + "]";
        }
    }
}
