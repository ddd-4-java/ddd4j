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
package io.ddd4j.sample.javalin.shiro.order.domain.service;

import io.ddd4j.sample.javalin.shiro.order.domain.model.Money;
import io.ddd4j.sample.javalin.shiro.order.domain.model.Order;
import io.ddd4j.sample.javalin.shiro.order.domain.repository.OrderRepository;

import java.util.Objects;

/**
 * 订单领域服务。
 *
 * <p>领域服务用于表达不属于单个聚合的领域逻辑。
 * 本例演示基于订单金额的 VIP 折扣计算：跨聚合的策略计算。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderDomainService {

    private final OrderRepository orderRepository;

    public OrderDomainService(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    /**
     * 根据买家历史订单数与订单金额计算折扣。
     *
     * <p>规则：
     * <ul>
     *   <li>订单总额 ≥ 1000 元：9 折</li>
     *   <li>订单总额 ≥ 500 元：95 折</li>
     *   <li>历史订单数 ≥ 10：额外 98 折</li>
     * </ul>
     */
    public Money calculateDiscount(String buyerId, Money total) {
        Objects.requireNonNull(buyerId, "buyerId must not be null");
        Objects.requireNonNull(total, "total must not be null");
        Money discounted = total;
        if (total.amount().compareTo(new java.math.BigDecimal("1000")) >= 0) {
            discounted = discounted.discount(10);
        } else if (total.amount().compareTo(new java.math.BigDecimal("500")) >= 0) {
            discounted = discounted.discount(5);
        }
        long historicalCount = orderRepository.findById(buyerId).isPresent() ? 1 : 0;
        if (historicalCount >= 10) {
            discounted = discounted.discount(2);
        }
        return discounted;
    }

    /**
     * 预览订单折扣。
     */
    public Money previewDiscount(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        return calculateDiscount(order.buyerId(), order.totalAmount());
    }
}