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
package io.ddd4j.sample.javalin.shiro.order.web.dto;
import io.ddd4j.sample.javalin.shiro.order.domain.model.OrderLine;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Order;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Value;


import io.ddd4j.sample.javalin.shiro.order.domain.model.Money;
import io.ddd4j.sample.javalin.shiro.order.domain.model.OrderStatus;

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
     * 从领域对象构造响应数据。
     *
     * @param order 订单聚合
     * @return 响应对象
     */
    public static OrderResponse from(Order order) {
        Money total = order.totalAmount();
        List<OrderLineResponse> lineResponses = java.util.Collections.unmodifiableList(
                order.lines().stream().map(OrderLineResponse::from).collect(java.util.stream.Collectors.toList()));
        return new OrderResponse(order.id(), order.orderNo(), order.buyerId(), order.buyerName(), order.status(), total.amount().toPlainString(), total.currency(), lineResponses);
    }

    /**
     * 订单行响应数据。
     */
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
         * 从领域对象构造响应数据。
         *
         * @param line 订单行
         * @return 响应对象
         */
        public static OrderLineResponse from(OrderLine line) {
            return new OrderLineResponse(line.id(), line.goodsId(), line.goodsName(), line.quantity(), line.unitPrice().amount().toPlainString(), line.unitPrice().currency());
        }

        /**
         * 创建订单行响应。
         *
         * @param id 资源标识
         * @param goodsId 商品标识
         * @param goodsName 商品名称
         * @param quantity 数量
         * @param unitPrice 商品单价
         * @param currency 币种
         */
        @JsonCreator()
        public OrderLineResponse(@JsonProperty("id") String id, @JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") String unitPrice, @JsonProperty("currency") String currency) {
            this.id = id;
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.currency = currency;
        }

        /**
         * 读取资源标识。
         *
         * @return 资源标识
         */
        @JsonProperty("id")
        public String id() {
            return id;
        }

        /**
         * 读取商品标识。
         *
         * @return 商品标识
         */
        @JsonProperty("goodsId")
        public String goodsId() {
            return goodsId;
        }

        /**
         * 读取商品名称。
         *
         * @return 商品名称
         */
        @JsonProperty("goodsName")
        public String goodsName() {
            return goodsName;
        }

        /**
         * 读取数量。
         *
         * @return 数量
         */
        @JsonProperty("quantity")
        public int quantity() {
            return quantity;
        }

        /**
         * 读取商品单价。
         *
         * @return 商品单价
         */
        @JsonProperty("unitPrice")
        public String unitPrice() {
            return unitPrice;
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

    }

    /**
     * 创建订单响应。
     *
     * @param id 资源标识
     * @param orderNo 订单编号
     * @param buyerId 买家标识
     * @param buyerName 买家名称
     * @param status 订单状态
     * @param totalAmount 总金额
     * @param currency 币种
     * @param lines 订单行列表
     */
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

    /**
     * 读取资源标识。
     *
     * @return 资源标识
     */
    @JsonProperty("id")
    public String id() {
        return id;
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

    /**
     * 读取订单状态。
     *
     * @return 订单状态
     */
    @JsonProperty("status")
    public OrderStatus status() {
        return status;
    }

    /**
     * 读取总金额。
     *
     * @return 总金额
     */
    @JsonProperty("totalAmount")
    public String totalAmount() {
        return totalAmount;
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

    /**
     * 读取订单行列表。
     *
     * @return 订单行列表
     */
    @JsonProperty("lines")
    public List<OrderLineResponse> lines() {
        return lines;
    }

}
