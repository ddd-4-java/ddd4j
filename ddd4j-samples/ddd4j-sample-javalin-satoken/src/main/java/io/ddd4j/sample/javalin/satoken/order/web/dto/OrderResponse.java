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
package io.ddd4j.sample.javalin.satoken.order.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.sample.javalin.satoken.order.domain.model.Money;
import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderStatus;

import java.util.List;

/**
 * 订单 REST 响应。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class OrderResponse {

    private static final long serialVersionUID = 0L;

    private final String id;

    private final String orderNo;

    private final String buyerId;

    private final String buyerName;

    private final OrderStatus status;

    private final String totalAmount;

    private final String currency;

    private final List<OrderLineResponse> lines;

    public static OrderResponse from(Order order) {
        Money total = order.totalAmount();
        List<OrderLineResponse> lineResponses = order.lines().stream().map(OrderLineResponse::from).toList();
        return new OrderResponse(order.id(), order.orderNo(), order.buyerId(), order.buyerName(), order.status(), total.amount().toPlainString(), total.currency(), lineResponses);
    }

    public final static class OrderLineResponse {

        private static final long serialVersionUID = 0L;

        private final String id;

        private final String goodsId;

        private final String goodsName;

        private final int quantity;

        private final String unitPrice;

        private final String currency;

        public static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(line.id(), line.goodsId(), line.goodsName(), line.quantity(), line.unitPrice().amount().toPlainString(), line.unitPrice().currency());
        }

        @JsonCreator()
        public OrderLineResponse(@JsonProperty("id") String id, @JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") String unitPrice, @JsonProperty("currency") String currency) {
            this.id = id;
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.currency = currency;
        }

        @JsonProperty("id")
        public String id() {
            return id;
        }

        @JsonProperty("goodsId")
        public String goodsId() {
            return goodsId;
        }

        @JsonProperty("goodsName")
        public String goodsName() {
            return goodsName;
        }

        @JsonProperty("quantity")
        public int quantity() {
            return quantity;
        }

        @JsonProperty("unitPrice")
        public String unitPrice() {
            return unitPrice;
        }

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
            OrderLineResponse other = (OrderLineResponse) obj;
            return Objects.equals(this.id, other.id) && Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && this.quantity == other.quantity && Objects.equals(this.unitPrice, other.unitPrice) && Objects.equals(this.currency, other.currency);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(goodsId);
            result = 31 * result + Objects.hashCode(goodsName);
            result = 31 * result + Integer.hashCode(quantity);
            result = 31 * result + Objects.hashCode(unitPrice);
            result = 31 * result + Objects.hashCode(currency);
            return result;
        }

        @Override
        public String toString() {
            return "OrderLineResponse[id=" + id + ", goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + ", currency=" + currency + "]";
        }
    }

    @JsonCreator()
    public OrderResponse(@JsonProperty("id") String id, @JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName, @JsonProperty("status") OrderStatus status, @JsonProperty("totalAmount") String totalAmount, @JsonProperty("currency") String currency, @JsonProperty("lines") List<OrderLineResponse> lines) {
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.lines = lines;
    }

    @JsonProperty("id")
    public String id() {
        return id;
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

    @JsonProperty("status")
    public OrderStatus status() {
        return status;
    }

    @JsonProperty("totalAmount")
    public String totalAmount() {
        return totalAmount;
    }

    @JsonProperty("currency")
    public String currency() {
        return currency;
    }

    @JsonProperty("lines")
    public List<OrderLineResponse> lines() {
        return lines;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        OrderResponse other = (OrderResponse) obj;
        return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount) && Objects.equals(this.currency, other.currency) && Objects.equals(this.lines, other.lines);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(orderNo);
        result = 31 * result + Objects.hashCode(buyerId);
        result = 31 * result + Objects.hashCode(buyerName);
        result = 31 * result + Objects.hashCode(status);
        result = 31 * result + Objects.hashCode(totalAmount);
        result = 31 * result + Objects.hashCode(currency);
        result = 31 * result + Objects.hashCode(lines);
        return result;
    }

    @Override
    public String toString() {
        return "OrderResponse[id=" + id + ", orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + ", status=" + status + ", totalAmount=" + totalAmount + ", currency=" + currency + ", lines=" + lines + "]";
    }
}
