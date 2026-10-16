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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.sample.javalin.satoken.order.domain.model.Money;
import io.ddd4j.sample.javalin.satoken.order.domain.model.Order;
import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderLine;
import io.ddd4j.sample.javalin.satoken.order.domain.model.OrderStatus;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单 REST 响应。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Value
public class OrderResponse {
    String id;
    String orderNo;
    String buyerId;
    String buyerName;
    OrderStatus status;
    String totalAmount;
    String currency;
    List<OrderLineResponse> lines;

    /**
     * 创建订单响应，并保留供 JSON 还原使用的字段映射。
     *
     * @param id 订单 ID
     * @param orderNo 订单号
     * @param buyerId 买家 ID
     * @param buyerName 买家名称
     * @param status 订单状态
     * @param totalAmount 总金额
     * @param currency 币种
     * @param lines 订单行
     */
    @JsonCreator
    public OrderResponse(@JsonProperty("id") String id,
                         @JsonProperty("orderNo") String orderNo,
                         @JsonProperty("buyerId") String buyerId,
                         @JsonProperty("buyerName") String buyerName,
                         @JsonProperty("status") OrderStatus status,
                         @JsonProperty("totalAmount") String totalAmount,
                         @JsonProperty("currency") String currency,
                         @JsonProperty("lines") List<OrderLineResponse> lines) {
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.lines = lines;
    }

    public static OrderResponse from(Order order) {
        Money total = order.totalAmount();
        List<OrderLineResponse> lineResponses = order.lines().stream()
                .map(OrderLineResponse::from)
                .collect(Collectors.toList());
        return new OrderResponse(
                order.id(),
                order.orderNo(),
                order.buyerId(),
                order.buyerName(),
                order.status(),
                total.amount().toPlainString(),
                total.currency(),
                lineResponses
        );
    }

    public String id() {
        return id;
    }

    public String orderNo() {
        return orderNo;
    }

    public String buyerId() {
        return buyerId;
    }

    public String buyerName() {
        return buyerName;
    }

    public OrderStatus status() {
        return status;
    }

    public String totalAmount() {
        return totalAmount;
    }

    public String currency() {
        return currency;
    }

    public List<OrderLineResponse> lines() {
        return lines;
    }

    @Value
    public static class OrderLineResponse {
        String id;
        String goodsId;
        String goodsName;
        int quantity;
        String unitPrice;
        String currency;

        /**
         * 创建订单行响应，并保留供 JSON 还原使用的字段映射。
         *
         * @param id 订单行 ID
         * @param goodsId 商品 ID
         * @param goodsName 商品名称
         * @param quantity 数量
         * @param unitPrice 商品单价
         * @param currency 币种
         */
        @JsonCreator
        public OrderLineResponse(@JsonProperty("id") String id,
                                 @JsonProperty("goodsId") String goodsId,
                                 @JsonProperty("goodsName") String goodsName,
                                 @JsonProperty("quantity") int quantity,
                                 @JsonProperty("unitPrice") String unitPrice,
                                 @JsonProperty("currency") String currency) {
            this.id = id;
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.currency = currency;
        }

        public static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(
                    line.id(),
                    line.goodsId(),
                    line.goodsName(),
                    line.quantity(),
                    line.unitPrice().amount().toPlainString(),
                    line.unitPrice().currency()
            );
        }

        public String id() {
            return id;
        }

        public String goodsId() {
            return goodsId;
        }

        public String goodsName() {
            return goodsName;
        }

        public int quantity() {
            return quantity;
        }

        public String unitPrice() {
            return unitPrice;
        }

        public String currency() {
            return currency;
        }
    }
}
