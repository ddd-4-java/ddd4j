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
package io.ddd4j.sample.order.domain;
import io.ddd4j.sample.order.domain.event.OrderShippedEvent;
import io.ddd4j.sample.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.order.domain.event.OrderLineAddedEvent;
import io.ddd4j.sample.order.domain.event.OrderCreatedEvent;
import io.ddd4j.sample.order.domain.event.OrderCancelledEvent;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.order.domain.event.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 管理订单明细及支付、取消、发货状态的聚合根。 */
public final class Order extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    /** 订单标识。 */
    private final String id;
    /** 订单号。 */
    private final String orderNo;
    /** 买家标识。 */
    private final String buyerId;
    /** 订单明细。 */
    private final List<OrderLine> lines = new ArrayList<>();
    /** 买家姓名。 */
    private String buyerName;
    /** 当前订单状态。 */
    private OrderStatus status;

    /**
     * 根据已有数据重建订单。
     *
     * @param id 订单标识
     * @param orderNo 订单号
     * @param buyerId 买家标识
     * @param buyerName 买家姓名
     * @param status 当前状态
     * @param lines 已有明细
     */
    public Order(String id, String orderNo, String buyerId, String buyerName,
                 OrderStatus status, List<OrderLine> lines) {
        if (StrKit.isBlank(id) || StrKit.isBlank(orderNo) || StrKit.isBlank(buyerId)) {
            throw new IllegalArgumentException("order identifiers must not be blank");
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
     * 创建草稿订单并登记创建事件。
     *
     * @param orderNo 订单号
     * @param buyerId 买家标识
     * @param buyerName 买家姓名
     * @return 新草稿订单
     */
    public static Order draft(String orderNo, String buyerId, String buyerName) {
        Order order = new Order(UUID.randomUUID().toString(), orderNo, buyerId, buyerName,
                OrderStatus.DRAFT, List.of());
        order.registerEvent(new OrderCreatedEvent(order.id));
        return order;
    }

    @Override
    public String id() {
        return id;
    }

    /** @return 订单号 */
    public String orderNo() {
        return orderNo;
    }

    /** @return 买家标识 */
    public String buyerId() {
        return buyerId;
    }

    /** @return 买家姓名 */
    public String buyerName() {
        return buyerName;
    }

    /** @return 订单状态 */
    public OrderStatus status() {
        return status;
    }

    /** @return 不可变的订单明细副本 */
    public List<OrderLine> lines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /** @return 人民币订单总金额 */
    public Money totalAmount() {
        return lines.stream().map(OrderLine::subtotal).reduce(Money.zero("CNY"), Money::add);
    }

    /** @param buyerName 新买家姓名 */
    public void renameBuyer(String buyerName) {
        if (StrKit.isBlank(buyerName)) {
            throw new IllegalArgumentException("buyerName must not be blank");
        }
        this.buyerName = buyerName;
    }

    /**
     * 向草稿订单添加商品明细。
     *
     * @param goodsId 商品标识
     * @param goodsName 商品名称
     * @param quantity 正数数量
     * @param unitPrice 商品单价
     */
    public void addLine(String goodsId, String goodsName, int quantity, Money unitPrice) {
        assertDraft();
        lines.add(OrderLine.create(goodsId, goodsName, quantity, unitPrice));
        registerEvent(new OrderLineAddedEvent(id));
    }

    /** 支付包含明细的草稿订单。 */
    public void pay() {
        assertDraft();
        if (lines.isEmpty()) {
            throw new IllegalStateException("order line must not be empty");
        }
        status = OrderStatus.PAID;
        registerEvent(new OrderPaidEvent(id));
    }

    /** 取消尚未发货的订单。 */
    public void cancel() {
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("shipped order cannot be cancelled");
        }
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelledEvent(id));
    }

    /** 将已支付的订单标记为发货。 */
    public void ship() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("only paid order can be shipped");
        }
        status = OrderStatus.SHIPPED;
        registerEvent(new OrderShippedEvent(id));
    }

    private void assertDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("only draft order can be changed");
        }
    }
}
