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
package io.ddd4j.sample.quarkus.cqrs.order.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.sample.quarkus.cqrs.order.application.AddOrderLineCommand;
import io.ddd4j.sample.quarkus.cqrs.order.application.CreateOrderCommand;
import io.ddd4j.sample.quarkus.cqrs.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.repository.OrderRepository;
import io.ddd4j.sample.quarkus.cqrs.order.domain.service.OrderDomainService;
import io.ddd4j.sample.quarkus.cqrs.order.web.dto.OrderResponse;
import io.ddd4j.web.quarkus.TenantAwareResource;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 订单 JAX-RS 资源：演示 ddd4j-web-quarkus 的资源基类与 Quarkus REST 集成。
 *
 * <p>继承 {@link TenantAwareResource}，可直接调用 {@code getTenantId()} /
 * {@code getLang()} / {@code ok(data)} / {@code fail(msg)} 等辅助方法，
 * 同时复用 ddd4j 的统一响应包装 {@link io.ddd4j.core.api.R}。
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET    /orders/{id}}          - 查询订单</li>
 *   <li>{@code GET    /orders/orderNo/{no}}  - 按订单编号查询</li>
 *   <li>{@code POST   /orders}               - 创建草稿订单</li>
 *   <li>{@code POST   /orders/{id}/lines}    - 添加订单行</li>
 *   <li>{@code POST   /orders/{id}:pay}      - 支付订单</li>
 *   <li>{@code POST   /orders/{id}:ship}     - 发货订单</li>
 *   <li>{@code POST   /orders/{id}:cancel}   - 取消订单</li>
 *   <li>{@code POST   /orders/cancel-all}    - 批量取消买家草稿订单</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <p>Resource 层只做协议适配（HTTP ↔ 应用服务入参 / 响应），不包含业务规则。
 * 所有业务方法都委托给 {@link OrderApplicationService} 与 {@link OrderDomainService}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource extends TenantAwareResource {

    private final OrderApplicationService applicationService;
    private final OrderDomainService domainService;
    private final OrderRepository repository;

    /**
     * @param applicationService 订单应用服务
     * @param domainService 订单领域服务
     * @param repository 订单仓储
     */
    @Inject
    public OrderResource(OrderApplicationService applicationService,
                         OrderDomainService domainService,
                         OrderRepository repository) {
        this.applicationService = applicationService;
        this.domainService = domainService;
        this.repository = repository;
    }

    /**
     * 按 ID 查询订单。
     *
     * @param id 订单 ID
     * @return 订单聚合（不存在返回 404）
     */
    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") String id) {
        Optional<Order> order = repository.findById(id);
        return order.map(OrderResponse::from)
                .map(this::ok)
                .orElseGet(() -> notFound("order not found: " + id));
    }

    /**
     * 按订单编号查询订单。
     *
     * @param orderNo 订单号
     * @return 订单响应
     */
    @GET
    @Path("/orderNo/{orderNo}")
    public Response getByOrderNo(@PathParam("orderNo") String orderNo) {
        return repository.findByOrderNo(orderNo)
                .map(OrderResponse::from)
                .map(this::ok)
                .orElseGet(() -> notFound("order not found by orderNo: " + orderNo));
    }

    /**
     * 创建草稿订单。
     *
     * @param request 创建订单请求
     * @return 新订单响应
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateOrderRequest request) {
        Order order = applicationService.createDraft(
                new CreateOrderCommand(request.orderNo(), request.buyerId(), request.buyerName()));
        return ok(OrderResponse.from(order));
    }

    /**
     * 添加订单行。
     *
     * @param id 订单标识
     * @param request 添加明细请求
     * @return 更新后的订单响应
     */
    @POST
    @Path("/{id}/lines")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addLine(@PathParam("id") String id, AddLineRequest request) {
        Order order = applicationService.addLine(
                new AddOrderLineCommand(id, request.goodsId(), request.goodsName(),
                        request.quantity(), request.unitPrice()));
        return ok(OrderResponse.from(order));
    }

    /**
     * 支付订单。
     *
     * @param id 订单标识
     * @return 支付后的订单响应
     */
    @POST
    @Path("/{id}:pay")
    public Response pay(@PathParam("id") String id) {
        return ok(OrderResponse.from(applicationService.pay(id)));
    }

    /**
     * 发货订单。
     *
     * @param id 订单标识
     * @return 发货后的订单响应
     */
    @POST
    @Path("/{id}:ship")
    public Response ship(@PathParam("id") String id) {
        return ok(OrderResponse.from(applicationService.ship(id)));
    }

    /**
     * 取消订单。
     *
     * @param id 订单标识
     * @return 取消后的订单响应
     */
    @POST
    @Path("/{id}:cancel")
    public Response cancel(@PathParam("id") String id) {
        return ok(OrderResponse.from(applicationService.cancel(id)));
    }

    /**
     * 批量取消买家草稿订单（演示领域服务与 SPI）。
     *
     * @param request 批量取消请求
     * @return 取消数量响应
     */
    @POST
    @Path("/cancel-all")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cancelAllDrafts(CancelAllRequest request) {
        int cancelled = domainService.cancelAllDraftsOf(request.buyerId());
        return ok(cancelled);
    }

    // ========== 请求 DTO ==========

    /**
     * 创建订单请求。
     */
    public final static class CreateOrderRequest {

        private static final long serialVersionUID = 0L;

        private final String orderNo;

        private final String buyerId;

        private final String buyerName;

        /**
         * @param orderNo 订单号
         * @param buyerId 买家标识
         * @param buyerName 买家姓名
         */
        @JsonCreator()
        public CreateOrderRequest(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
        }

        /** @return 订单号 */
        @JsonProperty("orderNo")
        public String orderNo() {
            return orderNo;
        }

        /** @return 买家标识 */
        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

        /** @return 买家姓名 */
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

    /**
     * 添加订单行请求。
     */
    public final static class AddLineRequest {

        private static final long serialVersionUID = 0L;

        private final String goodsId;

        private final String goodsName;

        private final int quantity;

        private final BigDecimal unitPrice;

        /**
         * @param goodsId 商品标识
         * @param goodsName 商品名称
         * @param quantity 商品数量
         * @param unitPrice 商品单价
         */
        @JsonCreator()
        public AddLineRequest(@JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice) {
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        /** @return 商品标识 */
        @JsonProperty("goodsId")
        public String goodsId() {
            return goodsId;
        }

        /** @return 商品名称 */
        @JsonProperty("goodsName")
        public String goodsName() {
            return goodsName;
        }

        /** @return 商品数量 */
        @JsonProperty("quantity")
        public int quantity() {
            return quantity;
        }

        /** @return 商品单价 */
        @JsonProperty("unitPrice")
        public BigDecimal unitPrice() {
            return unitPrice;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            AddLineRequest other = (AddLineRequest) obj;
            return Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && this.quantity == other.quantity && Objects.equals(this.unitPrice, other.unitPrice);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(goodsId);
            result = 31 * result + Objects.hashCode(goodsName);
            result = 31 * result + Integer.hashCode(quantity);
            result = 31 * result + Objects.hashCode(unitPrice);
            return result;
        }

        @Override
        public String toString() {
            return "AddLineRequest[goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + "]";
        }
    }

    /**
     * 批量取消请求。
     */
    public final static class CancelAllRequest {

        private static final long serialVersionUID = 0L;

        private final String buyerId;

        /** @param buyerId 买家标识 */
        @JsonCreator()
        public CancelAllRequest(@JsonProperty("buyerId") String buyerId) {
            this.buyerId = buyerId;
        }

        /** @return 买家标识 */
        @JsonProperty("buyerId")
        public String buyerId() {
            return buyerId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            CancelAllRequest other = (CancelAllRequest) obj;
            return Objects.equals(this.buyerId, other.buyerId);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(buyerId);
            return result;
        }

        @Override
        public String toString() {
            return "CancelAllRequest[buyerId=" + buyerId + "]";
        }
    }
}
