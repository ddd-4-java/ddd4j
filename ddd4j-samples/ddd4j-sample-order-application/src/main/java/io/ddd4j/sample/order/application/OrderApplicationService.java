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
package io.ddd4j.sample.order.application;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.sample.order.domain.Money;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/** 协调订单写操作、查询投影、幂等处理和 Outbox 持久化的应用服务。 */
@Slf4j
public class OrderApplicationService {

    private static final Duration PAYMENT_IDEMPOTENCY_TTL = Duration.ofMinutes(10);

    private final OrderRepository repository;
    private final OutboxPort outbox;
    private final OrderReadModelPort readModels;
    private final IdempotencyPort idempotency;
    private final OrderTransactionPort transaction;

    /**
     * 使用直通事务边界创建应用服务。
     *
     * @param repository 订单存储
     * @param outbox 待发布消息存储
     * @param readModels 查询模型端口
     * @param idempotency 幂等端口
     */
    public OrderApplicationService(OrderRepository repository, OutboxPort outbox,
                                   OrderReadModelPort readModels, IdempotencyPort idempotency) {
        this(repository, outbox, readModels, idempotency, OrderTransactionPort.noop());
    }

    /**
     * 使用指定事务边界创建应用服务。
     *
     * @param repository 订单存储
     * @param outbox 待发布消息存储
     * @param readModels 查询模型端口
     * @param idempotency 幂等端口
     * @param transaction 订单写入事务边界
     */
    public OrderApplicationService(OrderRepository repository, OutboxPort outbox,
                                   OrderReadModelPort readModels, IdempotencyPort idempotency,
                                   OrderTransactionPort transaction) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.readModels = Objects.requireNonNull(readModels, "readModels must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
    }

    /**
     * @param command 创建订单命令
     * @return 新草稿订单
     */
    public Order create(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = Order.draft(command.orderNo(), command.buyerId(), command.buyerName());
        persist(order);
        return order;
    }

    /**
     * @param command 添加明细命令
     * @return 更新后的订单
     */
    public Order addLine(AddOrderLineCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Order order = load(command.orderId());
        order.addLine(command.goodsId(), command.goodsName(), command.quantity(), Money.cny(command.unitPrice()));
        persist(order);
        return order;
    }

    /**
     * 幂等地支付订单。
     *
     * @param orderId 订单标识
     * @param idempotencyKey 支付幂等键
     * @return 支付后的订单
     */
    public Order pay(String orderId, String idempotencyKey) {
        String key = "order:payment:" + Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (!idempotency.acquire(key, PAYMENT_IDEMPOTENCY_TTL)) {
            return load(orderId);
        }
        try {
            Order order = load(orderId);
            order.pay();
            persist(order);
            idempotency.complete(key, order.id());
            return order;
        } catch (RuntimeException exception) {
            idempotency.release(key);
            throw exception;
        }
    }

    /**
     * @param orderId 订单标识
     * @return 取消后的订单
     */
    public Order cancel(String orderId) {
        Order order = load(orderId);
        order.cancel();
        persist(order);
        return order;
    }

    /**
     * @param orderId 订单标识
     * @return 发货后的订单
     */
    public Order ship(String orderId) {
        Order order = load(orderId);
        order.ship();
        persist(order);
        return order;
    }

    /**
     * @param orderId 订单标识
     * @return 订单查询模型
     */
    public OrderReadModel find(String orderId) {
        return readModels.findProjectionById(orderId).orElseGet(() -> toReadModel(load(orderId)));
    }

    /**
     * @param orderNo 订单号
     * @return 订单查询模型
     */
    public OrderReadModel findByOrderNo(String orderNo) {
        return repository.findByOrderNo(orderNo)
                .map(this::toReadModel)
                .orElseThrow(() -> new NoSuchElementException("order not found: " + orderNo));
    }

    /**
     * @param query 分页查询条件
     * @return 匹配的订单查询模型
     */
    public List<OrderReadModel> query(OrderQuery query) {
        return readModels.query(Objects.requireNonNull(query, "query must not be null"));
    }

    private Order load(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("order not found: " + orderId));
    }

    private void persist(Order order) {
        List<DomainEvent<?>> events = List.copyOf(order.domainEvents());
        transaction.execute(() -> {
            repository.save(order);
            outbox.append(events.stream().map(event -> new OutboxMessage(UUID.randomUUID().toString(), order.id(),
                    event.getClass().getName(), event, Instant.now())).toList());
            readModels.project(toReadModel(order));
        });
        order.clearDomainEvents();
        log.debug("Persisted order {} with {} outbox events", order.id(), events.size());
    }

    private OrderReadModel toReadModel(Order order) {
        return new OrderReadModel(order.id(), order.orderNo(), order.buyerId(), order.buyerName(),
                order.status(), order.totalAmount().amount());
    }
}
