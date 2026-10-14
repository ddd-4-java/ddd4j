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
package io.ddd4j.sample.micronaut;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.error.WebStatusException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Micronaut HTTP 到共享 Order Application 的协议适配层。
 */
@Controller("/api/orders")
public class OrderController {

    private final OrderApplicationService applicationService;

    /** @param applicationService 订单应用服务 */
    public OrderController(OrderApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    /**
     * @param request 创建订单请求
     * @return 新订单响应
     */
    @Post
    public HttpResponse<R<OrderReadModel>> create(@Body CreateOrderRequest request) {
        OrderReadModel response = applicationService.find(applicationService.create(
                new CreateOrderCommand(request.orderNo(), request.buyerId(), request.buyerName())).id());
        return HttpResponse.created(R.ok(response));
    }

    /**
     * @param orderId 订单标识
     * @return 订单查询模型
     */
    @Get("/{orderId}")
    public R<OrderReadModel> find(@PathVariable String orderId) {
        return R.ok(applicationService.find(orderId));
    }

    /**
     * @param buyerId 可选买家标识
     * @param status 可选订单状态
     * @param page 页码
     * @param size 每页数量
     * @return 订单查询模型列表
     */
    @Get
    public R<List<OrderReadModel>> query(@QueryValue(defaultValue = "") String buyerId,
                                         @QueryValue(defaultValue = "") String status,
                                         @QueryValue(defaultValue = "1") int page,
                                         @QueryValue(defaultValue = "20") int size) {
        OrderStatus orderStatus = StrKit.isBlank(status) ? null : OrderStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return R.ok(applicationService.query(new OrderQuery(
                StrKit.isBlank(buyerId) ? null : buyerId, orderStatus, page, size)));
    }

    /**
     * @param orderId 订单标识
     * @param request 添加明细请求
     * @return 更新后的订单查询模型
     */
    @Post("/{orderId}/lines")
    public R<OrderReadModel> addLine(@PathVariable String orderId, @Body AddOrderLineRequest request) {
        applicationService.addLine(new AddOrderLineCommand(orderId, request.goodsId(), request.goodsName(),
                request.quantity(), request.unitPrice()));
        return R.ok(applicationService.find(orderId));
    }

    /**
     * @param orderId 订单标识
     * @param idempotencyKey 支付幂等键
     * @return 支付后的订单查询模型
     */
    @Post("/{orderId}/pay")
    public R<OrderReadModel> pay(@PathVariable String orderId,
                                 @Header(WebHeaders.IDEMPOTENCY_KEY) String idempotencyKey) {
        if (StrKit.isBlank(idempotencyKey)) {
            throw new WebStatusException(400, "Idempotency-Key is required");
        }
        applicationService.pay(orderId, idempotencyKey);
        return R.ok(applicationService.find(orderId));
    }

    /** 创建订单的 HTTP 请求。 */
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

    /** 添加订单明细的 HTTP 请求。 */
    public final static class AddOrderLineRequest {

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
        public AddOrderLineRequest(@JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice) {
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
            AddOrderLineRequest other = (AddOrderLineRequest) obj;
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
            return "AddOrderLineRequest[goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + "]";
        }
    }
}
