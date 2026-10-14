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
package io.ddd4j.sample.order.jdbc;

import io.ddd4j.mq.delivery.MQDeliveryPolicy;
import io.ddd4j.mq.delivery.MQOutboxDispatchResult;
import io.ddd4j.mq.delivery.MQOutboxDispatcher;
import io.ddd4j.mq.delivery.MQOutboxStore;
import io.ddd4j.sample.order.application.IntegrationEventPublisher;
import io.ddd4j.sample.order.application.OutboxMessage;

import java.time.Clock;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * 订单样例的默认可靠 Outbox 发布入口。
 *
 * <p>公共 Dispatcher 在短事务中领取和确认；broker 发送发生在两个事务之间。
 * 从订单表重建完整消息，保持原有 Kafka 事件封装和聚合 ID 不变。
 */
public final class ReliableOrderOutboxPublisher {

    private final MQOutboxDispatcher dispatcher;
    private final String leaseOwner;
    private final Clock clock;
    private final BooleanSupplier transactionActive;

    /**
     * 创建从订单 Outbox 读取并可靠发布消息的入口。
     *
     * @param transaction 订单事务端口
     * @param outbox 持久化的订单 Outbox
     * @param publisher 业务消息发布器
     * @param leaseOwner 本实例领取消息时使用的租约标识
     * @param clock 投递时钟
     */
    public ReliableOrderOutboxPublisher(JdbcOrderTransactionPort transaction, JdbcOutboxPort outbox,
                                       IntegrationEventPublisher publisher, String leaseOwner, Clock clock) {
        this(new JdbcMQOutboxStore(transaction), outbox::findById, publisher, leaseOwner, clock,
                transaction::isTransactionActive);
    }

    ReliableOrderOutboxPublisher(MQOutboxStore store, Function<String, OutboxMessage> loadMessage,
                                IntegrationEventPublisher publisher, String leaseOwner, Clock clock) {
        this(store, loadMessage, publisher, leaseOwner, clock, () -> false);
    }

    private ReliableOrderOutboxPublisher(MQOutboxStore store, Function<String, OutboxMessage> loadMessage,
                                        IntegrationEventPublisher publisher, String leaseOwner, Clock clock,
                                        BooleanSupplier transactionActive) {
        Objects.requireNonNull(loadMessage, "loadMessage must not be null");
        Objects.requireNonNull(publisher, "publisher must not be null");
        this.dispatcher = new MQOutboxDispatcher(store,
                record -> publisher.publish(loadMessage.apply(record.messageId())),
                MQDeliveryPolicy.productionDefault());
        this.leaseOwner = Objects.requireNonNull(leaseOwner, "leaseOwner must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.transactionActive = Objects.requireNonNull(transactionActive, "transactionActive must not be null");
    }

    /**
     * 发布一批已提交订单事件。调用方应在业务事务外执行。
     *
     * @param limit 本轮最多领取数量
     * @return 公共可靠投递结果
     */
    public MQOutboxDispatchResult publishPending(int limit) {
        if (transactionActive.getAsBoolean()) {
            throw new IllegalStateException("Reliable order outbox dispatch must execute outside the order transaction");
        }
        return dispatcher.dispatch(leaseOwner, limit, clock.instant());
    }
}
