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

import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.IdempotencyPort;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 使用真实 PostgreSQL 验证订单、Outbox 与读模型的同事务回滚。 */
@Testcontainers
class JdbcOrderAtomicityTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void failureAfterProjectionMustRollBackOrderOutboxAndReadModel() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcOrderReadModelPort delegate = new JdbcOrderReadModelPort(transaction);
        AtomicReference<String> orderId = new AtomicReference<>();
        OrderReadModelPort failingProjection = new OrderReadModelPort() {
            @Override
            public void project(OrderReadModel order) {
                delegate.project(order);
                orderId.set(order.id());
                throw new IllegalStateException("injected failure after projection write");
            }

            @Override
            public Optional<OrderReadModel> findProjectionById(String id) {
                return delegate.findProjectionById(id);
            }

            @Override
            public List<OrderReadModel> query(OrderQuery query) {
                return delegate.query(query);
            }
        };
        IdempotencyPort unusedIdempotency = new IdempotencyPort() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                return true;
            }

            @Override
            public void complete(String key, Object result) {
            }

            @Override
            public void release(String key) {
            }
        };
        OrderApplicationService service = new OrderApplicationService(
                new JdbcOrderRepository(transaction), new JdbcOutboxPort(transaction, new ObjectMapper()),
                failingProjection, unusedIdempotency, transaction);
        String orderNo = "ROLLBACK-" + UUID.randomUUID();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.create(new CreateOrderCommand(orderNo, "buyer-rollback", "Rollback Test")));
        assertThat(failure).hasMessage("injected failure after projection write");
        assertThat(orderId.get()).isNotNull();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_orders WHERE id = ?", orderId.get())).isZero();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_order_outbox WHERE aggregate_id = ?", orderId.get()))
                .isZero();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_order_read_models WHERE id = ?", orderId.get()))
                .isZero();
    }

    @Test
    void failureAfterOrderWriteMustRollBackTheWriteModel() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcOrderRepository delegate = new JdbcOrderRepository(transaction);
        AtomicReference<String> orderId = new AtomicReference<>();
        OrderRepository failingRepository = new OrderRepository() {
            @Override
            public void save(Order order) {
                delegate.save(order);
                orderId.set(order.id());
                throw new IllegalStateException("injected failure after order write");
            }

            @Override
            public Optional<Order> findById(String id) {
                return delegate.findById(id);
            }

            @Override
            public Optional<Order> findByOrderNo(String orderNo) {
                return delegate.findByOrderNo(orderNo);
            }

            @Override
            public List<Order> findAll(int offset, int limit) {
                return delegate.findAll(offset, limit);
            }

            @Override
            public long count() {
                return delegate.count();
            }
        };
        IdempotencyPort unusedIdempotency = new IdempotencyPort() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                return true;
            }

            @Override
            public void complete(String key, Object result) {
            }

            @Override
            public void release(String key) {
            }
        };
        OrderApplicationService service = new OrderApplicationService(
                failingRepository, new JdbcOutboxPort(transaction, new ObjectMapper()),
                new JdbcOrderReadModelPort(transaction), unusedIdempotency, transaction);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.create(new CreateOrderCommand(
                        "ORDER-WRITE-ROLLBACK-" + UUID.randomUUID(), "buyer-rollback", "Rollback Test")));
        assertThat(failure).hasMessage("injected failure after order write");
        assertThat(orderId.get()).isNotNull();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_orders WHERE id = ?", orderId.get())).isZero();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_order_outbox WHERE aggregate_id = ?", orderId.get()))
                .isZero();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_order_read_models WHERE id = ?", orderId.get()))
                .isZero();
    }

    @Test
    void failureAfterOutboxWriteMustRollBackOrderAndOutbox() {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcOutboxPort delegate = new JdbcOutboxPort(transaction, new ObjectMapper());
        JdbcOrderReadModelPort readModels = new JdbcOrderReadModelPort(transaction);
        AtomicReference<String> orderId = new AtomicReference<>();
        OutboxPort failingOutbox = new OutboxPort() {
            @Override
            public void append(List<OutboxMessage> messages) {
                delegate.append(messages);
                orderId.set(messages.get(0).aggregateId());
                throw new IllegalStateException("injected failure after outbox write");
            }

            @Override
            public List<OutboxMessage> pending(int limit) {
                return delegate.pending(limit);
            }

            @Override
            public void markPublished(String messageId) {
                delegate.markPublished(messageId);
            }

            @Override
            public void markFailed(String messageId, String reason) {
                delegate.markFailed(messageId, reason);
            }
        };
        IdempotencyPort unusedIdempotency = new IdempotencyPort() {
            @Override
            public boolean acquire(String key, Duration ttl) {
                return true;
            }

            @Override
            public void complete(String key, Object result) {
            }

            @Override
            public void release(String key) {
            }
        };
        OrderApplicationService service = new OrderApplicationService(
                new JdbcOrderRepository(transaction), failingOutbox, readModels,
                unusedIdempotency, transaction);
        String orderNo = "OUTBOX-ROLLBACK-" + UUID.randomUUID();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.create(new CreateOrderCommand(orderNo, "buyer-rollback", "Rollback Test")));
        assertThat(failure).hasMessage("injected failure after outbox write");
        assertThat(orderId.get()).isNotNull();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_orders WHERE id = ?", orderId.get())).isZero();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_order_outbox WHERE aggregate_id = ?", orderId.get()))
                .isZero();
        assertThat(count(transaction, "SELECT COUNT(*) FROM sample_order_read_models WHERE id = ?", orderId.get()))
                .isZero();
    }

    private long count(JdbcOrderTransactionPort transaction, String sql, String orderId) {
        return transaction.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, orderId);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getLong(1);
                }
            }
        });
    }
}
