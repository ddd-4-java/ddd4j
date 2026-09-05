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

/** HTTP translation layer for the shared Order application. */
public final class OrderController {

    private final OrderApplicationService applicationService;

    public OrderController(OrderApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService,
                "applicationService must not be null");
    }

    public void routes() {
        post("/api/orders", context -> {
            CreateOrderRequest request = context.bodyAsClass(CreateOrderRequest.class);
            Order order = applicationService.create(new CreateOrderCommand(
                    request.getOrderNo(), request.getBuyerId(), request.getBuyerName()));
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

    private static int integer(String value, int defaultValue) {
        return StrKit.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }

    private static OrderResponse toResponse(Order order) {
        Money total = order.totalAmount();
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getBuyerId(), order.getBuyerName(),
                order.getStatus(), total.getAmount(), total.getCurrency(), order.lines().size());
    }

    public final class CreateOrderRequest {
    private final String orderNo;
    private final String buyerId;
    private final String buyerName;

    public CreateOrderRequest(String orderNo, String buyerId, String buyerName) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
    }

    public String getOrderNo() { return orderNo; }
    public String getBuyerId() { return buyerId; }
    public String getBuyerName() { return buyerName; }
}

    public final class AddOrderLineRequest {
    private final String goodsId;
    private final String goodsName;
    private final int quantity;
    private final BigDecimal unitPrice;

    public AddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getGoodsId() { return goodsId; }
    public String getGoodsName() { return goodsName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}

    public final class OrderResponse {
    private final String id;
    private final String orderNo;
    private final String buyerId;
    private final String buyerName;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String currency;
    private final int lineCount;

    public OrderResponse(String id, String orderNo, String buyerId, String buyerName, OrderStatus status, BigDecimal totalAmount, String currency, int lineCount) {
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.lineCount = lineCount;
    }

    public String getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getBuyerId() { return buyerId; }
    public String getBuyerName() { return buyerName; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public int getLineCount() { return lineCount; }
}
}
