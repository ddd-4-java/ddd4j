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
package io.ddd4j.sample.javalin.order.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.domain.Money;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.error.WebStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * HTTP translation layer for the shared Order application.
 */
public final class OrderController {

    private final OrderApplicationService applicationService;

    /** @param applicationService 订单应用服务 */
    public OrderController(OrderApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService,
                "applicationService must not be null");
    }

    private static int integer(String value, int defaultValue) {
        return StrKit.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }

    private static OrderResponse toResponse(Order order) {
        Money total = order.totalAmount();
        return new OrderResponse(order.id(), order.orderNo(), order.buyerId(), order.buyerName(),
                order.status(), total.amount(), total.currency(), order.lines().size());
    }

    /** 注册订单 HTTP 路由。 */
    public void routes() {
        post("/api/orders", context -> {
            CreateOrderRequest request = context.bodyAsClass(CreateOrderRequest.class);
            Order order = applicationService.create(new CreateOrderCommand(
                    request.orderNo(), request.buyerId(), request.buyerName()));
            context.status(201).json(R.ok(toResponse(order)));
        });
        get("/api/orders/by-no", context -> context.json(R.ok(applicationService.findByOrderNo(
                context.queryParam("orderNo")))));
        get("/api/orders", context -> {
            String buyerId = context.queryParam("buyerId");
            String status = context.queryParam("status");
            int page = integer(context.queryParam("page"), 1);
            int size = integer(context.queryParam("size"), 20);
            OrderStatus orderStatus = StrKit.isBlank(status)
                    ? null : OrderStatus.valueOf(status.toUpperCase(Locale.ROOT));
            context.json(R.ok(applicationService.query(new OrderQuery(buyerId, orderStatus, page, size))));
        });
        get("/api/orders/{id}", context -> context.json(R.ok(applicationService.find(
                context.pathParam("id")))));
        post("/api/orders/{id}/lines", context -> {
            AddOrderLineRequest request = context.bodyAsClass(AddOrderLineRequest.class);
            Order order = applicationService.addLine(new AddOrderLineCommand(context.pathParam("id"),
                    request.goodsId(), request.goodsName(), request.quantity(), request.unitPrice()));
            context.json(R.ok(toResponse(order)));
        });
        post("/api/orders/{id}/pay", context -> {
            String idempotencyKey = context.header(WebHeaders.IDEMPOTENCY_KEY);
            if (StrKit.isBlank(idempotencyKey)) {
                throw new WebStatusException(400, "Idempotency-Key is required");
            }
            context.json(R.ok(toResponse(applicationService.pay(context.pathParam("id"), idempotencyKey))));
        });
        post("/api/orders/{id}/ship", context -> context.json(R.ok(toResponse(
                applicationService.ship(context.pathParam("id"))))));
        post("/api/orders/{id}/cancel", context -> context.json(R.ok(toResponse(
                applicationService.cancel(context.pathParam("id"))))));
    }

    /** 创建订单的 HTTP 请求。 */
    public static class CreateOrderRequest {

        private static final long serialVersionUID = 0L;

        private String orderNo;

        private String buyerId;

        private String buyerName;

        /** 创建空请求，供反序列化使用。 */
        public CreateOrderRequest() {
        }

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

        /** @return 订单号 */
        public String getOrderNo() {
            return orderNo;
        }

        /** @param orderNo 订单号 */
        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        /** @return 买家标识 */
        public String getBuyerId() {
            return buyerId;
        }

        /** @param buyerId 买家标识 */
        public void setBuyerId(String buyerId) {
            this.buyerId = buyerId;
        }

        /** @return 买家姓名 */
        public String getBuyerName() {
            return buyerName;
        }

        /** @param buyerName 买家姓名 */
        public void setBuyerName(String buyerName) {
            this.buyerName = buyerName;
        }

        /**
         * 判断另一对象是否可以参与相等比较。
         * @param other 待比较对象
         * @return 是否为同类请求或响应
         */
        protected boolean canEqual(Object other) {
            return other instanceof CreateOrderRequest;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CreateOrderRequest)) {
                return false;
            }
            CreateOrderRequest other = (CreateOrderRequest) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(getOrderNo(), other.getOrderNo())
                    && Objects.equals(getBuyerId(), other.getBuyerId())
                    && Objects.equals(getBuyerName(), other.getBuyerName());
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(getOrderNo()) ? 43 : getOrderNo().hashCode());
            result = 59 * result + (Objects.isNull(getBuyerId()) ? 43 : getBuyerId().hashCode());
            result = 59 * result + (Objects.isNull(getBuyerName()) ? 43 : getBuyerName().hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "OrderController.CreateOrderRequest(orderNo=" + getOrderNo() + ", buyerId=" + getBuyerId() + ", buyerName=" + getBuyerName() + ")";
        }
    }

    /** 添加订单明细的 HTTP 请求。 */
    public static class AddOrderLineRequest {

        private static final long serialVersionUID = 0L;

        private String goodsId;

        private String goodsName;

        private int quantity;

        private BigDecimal unitPrice;

        /** 创建空请求，供反序列化使用。 */
        public AddOrderLineRequest() {
        }

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

        /** @return 商品标识 */
        public String getGoodsId() {
            return goodsId;
        }

        /** @param goodsId 商品标识 */
        public void setGoodsId(String goodsId) {
            this.goodsId = goodsId;
        }

        /** @return 商品名称 */
        public String getGoodsName() {
            return goodsName;
        }

        /** @param goodsName 商品名称 */
        public void setGoodsName(String goodsName) {
            this.goodsName = goodsName;
        }

        /** @return 商品数量 */
        public int getQuantity() {
            return quantity;
        }

        /** @param quantity 商品数量 */
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        /** @return 商品单价 */
        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        /** @param unitPrice 商品单价 */
        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        /**
         * 判断另一对象是否可以参与相等比较。
         * @param other 待比较对象
         * @return 是否为同类请求或响应
         */
        protected boolean canEqual(Object other) {
            return other instanceof AddOrderLineRequest;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AddOrderLineRequest)) {
                return false;
            }
            AddOrderLineRequest other = (AddOrderLineRequest) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(getGoodsId(), other.getGoodsId())
                    && Objects.equals(getGoodsName(), other.getGoodsName())
                    && getQuantity() == other.getQuantity()
                    && Objects.equals(getUnitPrice(), other.getUnitPrice());
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(getGoodsId()) ? 43 : getGoodsId().hashCode());
            result = 59 * result + (Objects.isNull(getGoodsName()) ? 43 : getGoodsName().hashCode());
            result = 59 * result + getQuantity();
            result = 59 * result + (Objects.isNull(getUnitPrice()) ? 43 : getUnitPrice().hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "OrderController.AddOrderLineRequest(goodsId=" + getGoodsId() + ", goodsName=" + getGoodsName() + ", quantity=" + getQuantity() + ", unitPrice=" + getUnitPrice() + ")";
        }
    }

    /** 订单 HTTP 响应。 */
    public static class OrderResponse {

        private static final long serialVersionUID = 0L;

        private final String id;

        private final String orderNo;

        private final String buyerId;

        private final String buyerName;

        private final OrderStatus status;

        private final BigDecimal totalAmount;

        private final String currency;

        private final int lineCount;

        /**
         * @param id 订单标识
         * @param orderNo 订单号
         * @param buyerId 买家标识
         * @param buyerName 买家姓名
         * @param status 订单状态
         * @param totalAmount 订单总金额
         * @param currency 币种
         * @param lineCount 明细数量
         */
        @JsonCreator()
        public OrderResponse(@JsonProperty("id") String id, @JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName, @JsonProperty("status") OrderStatus status, @JsonProperty("totalAmount") BigDecimal totalAmount, @JsonProperty("currency") String currency, @JsonProperty("lineCount") int lineCount) {
            this.id = id;
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.status = status;
            this.totalAmount = totalAmount;
            this.currency = currency;
            this.lineCount = lineCount;
        }

        /** @return 订单标识 */
        @JsonProperty("id")
        public String id() {
            return id;
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

        /** @return 订单状态 */
        @JsonProperty("status")
        public OrderStatus status() {
            return status;
        }

        /** @return 订单总金额 */
        @JsonProperty("totalAmount")
        public BigDecimal totalAmount() {
            return totalAmount;
        }

        /** @return 币种 */
        @JsonProperty("currency")
        public String currency() {
            return currency;
        }

        /** @return 明细数量 */
        @JsonProperty("lineCount")
        public int lineCount() {
            return lineCount;
        }

        /** @return 订单标识 */
        public String getId() {
            return id;
        }

        /** @return 订单号 */
        public String getOrderNo() {
            return orderNo;
        }

        /** @return 买家标识 */
        public String getBuyerId() {
            return buyerId;
        }

        /** @return 买家姓名 */
        public String getBuyerName() {
            return buyerName;
        }

        /** @return 订单状态 */
        public OrderStatus getStatus() {
            return status;
        }

        /** @return 订单总金额 */
        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        /** @return 币种 */
        public String getCurrency() {
            return currency;
        }

        /** @return 明细数量 */
        public int getLineCount() {
            return lineCount;
        }

        /**
         * 判断另一对象是否可以参与相等比较。
         * @param other 待比较对象
         * @return 是否为同类请求或响应
         */
        protected boolean canEqual(Object other) {
            return other instanceof OrderResponse;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof OrderResponse)) {
                return false;
            }
            OrderResponse other = (OrderResponse) obj;
            if (!other.canEqual(this)) {
                return false;
            }
            return Objects.equals(getId(), other.getId())
                    && Objects.equals(getOrderNo(), other.getOrderNo())
                    && Objects.equals(getBuyerId(), other.getBuyerId())
                    && Objects.equals(getBuyerName(), other.getBuyerName())
                    && Objects.equals(getStatus(), other.getStatus())
                    && Objects.equals(getTotalAmount(), other.getTotalAmount())
                    && Objects.equals(getCurrency(), other.getCurrency())
                    && getLineCount() == other.getLineCount();
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 59 * result + (Objects.isNull(getId()) ? 43 : getId().hashCode());
            result = 59 * result + (Objects.isNull(getOrderNo()) ? 43 : getOrderNo().hashCode());
            result = 59 * result + (Objects.isNull(getBuyerId()) ? 43 : getBuyerId().hashCode());
            result = 59 * result + (Objects.isNull(getBuyerName()) ? 43 : getBuyerName().hashCode());
            result = 59 * result + (Objects.isNull(getStatus()) ? 43 : getStatus().hashCode());
            result = 59 * result + (Objects.isNull(getTotalAmount()) ? 43 : getTotalAmount().hashCode());
            result = 59 * result + (Objects.isNull(getCurrency()) ? 43 : getCurrency().hashCode());
            result = 59 * result + getLineCount();
            return result;
        }

        @Override
        public String toString() {
            return "OrderController.OrderResponse(id=" + getId() + ", orderNo=" + getOrderNo() + ", buyerId=" + getBuyerId() + ", buyerName=" + getBuyerName() + ", status=" + getStatus() + ", totalAmount=" + getTotalAmount() + ", currency=" + getCurrency() + ", lineCount=" + getLineCount() + ")";
        }
    }
}
