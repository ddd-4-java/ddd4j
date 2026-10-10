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
package io.ddd4j.sample.javalin.cqrs.order.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.cqrs.cache.OrderCacheService;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Money;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;

import java.util.Map;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * 订单 CQRS 读侧控制器 - 缓存增强版本（Javalin）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class OrderCQRSQueryController {

    private final OrderCacheService orderCacheService;

    public OrderCQRSQueryController(OrderCacheService orderCacheService) {
        this.orderCacheService = Objects.requireNonNull(orderCacheService, "orderCacheService must not be null");
    }

    /**
     * 路由注册入口。
     */
    public void routes() {
        // GET /api/orders/query/list
        get("/api/orders/query/list", ctx -> {
            int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);
            int pageSize = ctx.queryParamAsClass("pageSize", Integer.class).getOrDefault(10);
            ctx.json(R.ok(Map.of("page", page, "pageSize", pageSize, "note", "see /api/orders/query/stats")));
        });

        // GET /api/orders/query/stats
        get("/api/orders/query/stats", ctx -> ctx.json(R.ok(orderCacheService.getOrderStats())));

        // GET /api/orders/query/buyer/{buyerId}/count
        get("/api/orders/query/buyer/{buyerId}/count", ctx -> {
            String buyerId = ctx.pathParam("buyerId");
            long count = orderCacheService.getBuyerOrderCount(buyerId);
            ctx.json(R.ok(Map.of("buyerId", buyerId, "count", count)));
        });

        // GET /api/orders/query/detail/{id}
        get("/api/orders/query/detail/{id}", ctx -> {
            String id = ctx.pathParam("id");
            R<OrderResponse> body = orderCacheService.getOrderDetail(id)
                    .map(this::toResponse)
                    .orElseGet(() -> R.of("404", "order not found: " + id));
            ctx.json(body);
        });
    }

    private R<OrderResponse> toResponse(Order order) {
        return R.ok(new OrderResponse(
                order.id(),
                order.orderNo(),
                order.buyerId(),
                order.buyerName(),
                order.status().name(),
                order.totalAmount(),
                order.lines().size()
        ));
    }

    /**
     * 订单响应 record。
     */
    public final static class OrderResponse {

        private static final long serialVersionUID = 0L;

        private final String id;

        private final String orderNo;

        private final String buyerId;

        private final String buyerName;

        private final String status;

        private final Money totalAmount;

        private final int lineCount;

        @JsonCreator()
        public OrderResponse(@JsonProperty("id") String id, @JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName, @JsonProperty("status") String status, @JsonProperty("totalAmount") Money totalAmount, @JsonProperty("lineCount") int lineCount) {
            this.id = id;
            this.orderNo = orderNo;
            this.buyerId = buyerId;
            this.buyerName = buyerName;
            this.status = status;
            this.totalAmount = totalAmount;
            this.lineCount = lineCount;
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
        public String status() {
            return status;
        }

        @JsonProperty("totalAmount")
        public Money totalAmount() {
            return totalAmount;
        }

        @JsonProperty("lineCount")
        public int lineCount() {
            return lineCount;
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
            return Objects.equals(this.id, other.id) && Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName) && Objects.equals(this.status, other.status) && Objects.equals(this.totalAmount, other.totalAmount) && this.lineCount == other.lineCount;
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
            result = 31 * result + Integer.hashCode(lineCount);
            return result;
        }

        @Override
        public String toString() {
            return "OrderResponse[id=" + id + ", orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + ", status=" + status + ", totalAmount=" + totalAmount + ", lineCount=" + lineCount + "]";
        }
    }
}
