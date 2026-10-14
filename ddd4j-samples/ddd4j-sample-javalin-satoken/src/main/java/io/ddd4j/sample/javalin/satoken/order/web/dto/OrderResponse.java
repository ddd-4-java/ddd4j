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
import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderLine;
import io.ddd4j.sample.javalin.satoken.order.domain.model.Order;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.Value;

import io.ddd4j.sample.javalin.satoken.order.domain.model.Money;
import io.ddd4j.sample.javalin.satoken.order.domain.model.Order;
import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderLine;
import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderStatus;

import java.util.List;

/**
 * 订单 REST 响应。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Value
public class OrderResponse {

    private static final long serialVersionUID = 0L;

    private final String id;

    private final String orderNo;

    private final String buyerId;

    private final String buyerName;

    private final OrderStatus status;

    private final String totalAmount;

    private final String currency;

    private final List<OrderLineResponse> lines;

    /**
     * from() 方法。
     * @param order order 参数。
     * @return 当前对象自身，支持链式调用。
     */
    public static OrderResponse from(Order order) {
        Money total = order.totalAmount();
        List<OrderLineResponse> lineResponses = order.lines().stream().map(OrderLineResponse::from).toList();
        return new OrderResponse(order.id(), order.orderNo(), order.buyerId(), order.buyerName(), order.status(), total.amount().toPlainString(), total.currency(), lineResponses);
    }

    @Value
    public static class OrderLineResponse {

        private static final long serialVersionUID = 0L;

        private final String id;

        private final String goodsId;

        private final String goodsName;

        private final int quantity;

        private final String unitPrice;

        private final String currency;

        /**
         * from() 方法。
         * @param line line 参数。
         * @return 方法执行结果。
         */
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


}
