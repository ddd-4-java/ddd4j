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
package io.ddd4j.sample.quarkus.satoken.order.web.dto;

import io.ddd4j.sample.quarkus.satoken.order.domain.model.Money;
import io.ddd4j.sample.quarkus.satoken.order.domain.model.Order;
import io.ddd4j.sample.quarkus.satoken.order.domain.model.OrderLine;
import io.ddd4j.sample.quarkus.satoken.order.domain.model.OrderStatus;

import java.util.List;

/**
 * 订单 REST 响应。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record OrderResponse(String id, String orderNo, String buyerId, String buyerName,
                            OrderStatus status, String totalAmount, String currency,
                            List<OrderLineResponse> lines) {

    public static OrderResponse from(Order order) {
        Money total = order.totalAmount();
        List<OrderLineResponse> lineResponses = order.lines().stream()
                .map(OrderLineResponse::from)
                .toList();
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

    public record OrderLineResponse(String id, String goodsId, String goodsName,
                                    int quantity, String unitPrice, String currency) {

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
    }
}