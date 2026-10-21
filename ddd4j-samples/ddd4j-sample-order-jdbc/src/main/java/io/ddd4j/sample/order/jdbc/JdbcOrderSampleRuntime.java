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

import tools.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.application.IdempotencyPort;
import io.ddd4j.sample.order.application.IntegrationEventPublisher;
import io.ddd4j.sample.order.application.OrderApplicationService;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * JDBC 订单样例的运行接线，使用公共可靠 Outbox Dispatcher 发布已提交事件。
 *
 * <p>调用方提供已迁移的数据库、幂等端口与 broker 发布器，并在应用关闭时调用 {@link #close()}。
 * 本类不取得这些外部资源的所有权。
 */
public final class JdbcOrderSampleRuntime implements AutoCloseable {

    private final OrderApplicationService applicationService;
    private final ReliableOrderOutboxWorker worker;

    private JdbcOrderSampleRuntime(OrderApplicationService applicationService, ReliableOrderOutboxWorker worker) {
        this.applicationService = applicationService;
        this.worker = worker;
    }

    /**
     * 创建订单服务并启动可靠 Outbox 定时工作器。
     *
     * @param dataSource 已迁移订单表的 JDBC 数据源
     * @param objectMapper 订单消息序列化器
     * @param idempotency 支付幂等端口
     * @param publisher broker 业务消息发布器
     * @param leaseOwner 当前实例唯一且稳定的租约所有者
     * @param batchSize 每轮最多领取的消息数
     * @param interval 两轮调度间隔
     * @param clock 投递时钟
     * @return 已启动且需要关闭的订单样例运行实例
     */
    public static JdbcOrderSampleRuntime start(DataSource dataSource, ObjectMapper objectMapper,
                                               IdempotencyPort idempotency, IntegrationEventPublisher publisher,
                                               String leaseOwner, int batchSize, Duration interval, Clock clock) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(idempotency, "idempotency must not be null");
        Objects.requireNonNull(publisher, "publisher must not be null");

        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcOutboxPort outbox = new JdbcOutboxPort(transaction, objectMapper);
        OrderApplicationService service = new OrderApplicationService(new JdbcOrderRepository(transaction), outbox,
                new JdbcOrderReadModelPort(transaction), idempotency, transaction);
        ReliableOrderOutboxPublisher reliablePublisher = new ReliableOrderOutboxPublisher(transaction, outbox,
                publisher, leaseOwner, clock);
        ReliableOrderOutboxWorker worker = new ReliableOrderOutboxWorker(reliablePublisher, batchSize, interval);
        try {
            worker.start();
            return new JdbcOrderSampleRuntime(service, worker);
        } catch (RuntimeException exception) {
            worker.close();
            throw exception;
        }
    }

    /** @return 与可靠 Outbox 共用同一 JDBC 事务端口的订单应用服务。 */
    public OrderApplicationService applicationService() {
        return applicationService;
    }

    /** 停止后台投递；外部数据源、幂等端口和 broker 发布器由调用方关闭。 */
    @Override
    public void close() {
        worker.close();
    }
}
