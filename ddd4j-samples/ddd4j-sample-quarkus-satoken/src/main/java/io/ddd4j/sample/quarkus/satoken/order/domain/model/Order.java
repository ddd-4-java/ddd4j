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
package io.ddd4j.sample.quarkus.satoken.order.domain.model;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.satoken.order.domain.event.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单聚合根（充血模型）。
 *
 * <p>封装完整的业务行为与状态机不变式，所有业务方法在修改状态后通过 {@link #registerEvent} 注册领域事件，
 * 由应用服务统一发布。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Order extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    /**
     * 订单标识。
     */
    private final String id;
    /**
     * 订单编号。
     */
    private final String orderNo;
    /**
     * 买家标识。
     */
    private final String buyerId;
    /**
     * 订单行集合。
     */
    private final List<OrderLine> lines = new ArrayList<>();
    /**
     * 买家名称。
     */
    private String buyerName;
    /**
     * 订单状态。
     */
    private OrderStatus status;

    /**
     * 重建订单聚合。
     *
     * @param id 订单标识
     * @param orderNo 订单编号
     * @param buyerId 买家标识
     * @param buyerName 买家名称
     * @param status 订单状态
     * @param lines 订单行列表
     */
    public Order(String id, String orderNo, String buyerId, String buyerName, OrderStatus status, List<OrderLine> lines) {
        if (StrKit.isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (StrKit.isBlank(orderNo)) {
            throw new IllegalArgumentException("orderNo must not be blank");
        }
        if (StrKit.isBlank(buyerId)) {
            throw new IllegalArgumentException("buyerId must not be blank");
        }
        this.id = id;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        renameBuyer(buyerName);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (Objects.nonNull(lines)) {
            this.lines.addAll(lines);
        }
    }

    /**
     * 构造草稿订单。
     *
     * @param orderNo 订单编号
     * @param buyerId 买家标识
     * @param buyerName 买家名称
     * @return 草稿订单
     */
    public static Order draft(String orderNo, String buyerId, String buyerName) {
        Order order = new Order(UUID.randomUUID().toString(), orderNo, buyerId, buyerName, OrderStatus.DRAFT, Collections.emptyList());
        order.registerEvent(new OrderCreatedEvent(order.id()));
        return order;
    }

    @Override
    public String id() {
        return id;
    }

    /**
     * 读取订单编号。
     *
     * @return 订单编号
     */
    public String orderNo() {
        return orderNo;
    }

    /**
     * 读取买家标识。
     *
     * @return 买家标识
     */
    public String buyerId() {
        return buyerId;
    }

    /**
     * 读取买家名称。
     *
     * @return 买家名称
     */
    public String buyerName() {
        return buyerName;
    }

    /**
     * 读取订单状态。
     *
     * @return 订单状态
     */
    public OrderStatus status() {
        return status;
    }

    /**
     * 读取订单行列表。
     *
     * @return 订单行列表
     */
    public List<OrderLine> lines() {
        List<OrderLine> snapshot = new ArrayList<>(lines);
        for (OrderLine line : snapshot) {
            Objects.requireNonNull(line, "order line must not be null");
        }
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * 计算订单总金额。
     *
     * @return 订单总金额
     */
    public Money totalAmount() {
        return lines.stream()
                .map(OrderLine::subtotal)
                .reduce(Money.zero("CNY"), Money::add);
    }

    /**
     * 更新买家名称。
     *
     * @param buyerName 买家名称
     */
    public void renameBuyer(String buyerName) {
        if (StrKit.isBlank(buyerName)) {
            throw new IllegalArgumentException("buyerName must not be blank");
        }
        this.buyerName = buyerName;
    }

    /**
     * 向订单添加商品行。
     *
     * @param goodsId 商品标识
     * @param goodsName 商品名称
     * @param quantity 数量
     * @param unitPrice 商品单价
     */
    public void addLine(String goodsId, String goodsName, int quantity, Money unitPrice) {
        assertDraft();
        OrderLine line = OrderLine.create(goodsId, goodsName, quantity, unitPrice);
        lines.add(line);
        registerEvent(new OrderLineAddedEvent(id));
    }

    /**
     * 支付订单。
     */
    public void pay() {
        assertDraft();
        if (lines.isEmpty()) {
            throw new IllegalStateException("order line must not be empty");
        }
        status = OrderStatus.PAID;
        registerEvent(new OrderPaidEvent(id));
    }

    /**
     * 发货订单。
     */
    public void ship() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("only paid order can be shipped");
        }
        status = OrderStatus.SHIPPED;
        registerEvent(new OrderShippedEvent(id));
    }

    /**
     * 取消订单。
     */
    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("shipped order cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelledEvent(id));
    }

    private void assertDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("only draft order can be changed");
        }
    }
}
