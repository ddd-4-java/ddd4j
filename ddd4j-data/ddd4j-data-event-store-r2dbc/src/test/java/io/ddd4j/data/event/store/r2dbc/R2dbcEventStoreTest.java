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
package io.ddd4j.data.event.store.r2dbc;

import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.Batch;
import io.r2dbc.h2.CloseableConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionMetadata;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.TransactionDefinition;
import io.r2dbc.spi.ValidationDepth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link R2dbcEventStore} 集成测试——H2 内存数据库全量契约验证。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>append 后 read 单流按版本顺序返回</li>
 *   <li>乐观锁：expectedVersion 不匹配抛 {@link IllegalStateException} 且不落库</li>
 *   <li>readAll 按 position 升序 + limit 分页</li>
 *   <li>事件 payload 往返（写入→读出 event 内容一致，类型还原）</li>
 *   <li>多聚合 append 交错时全局 position 单调</li>
 * </ol>
 *
 * <p>使用 r2dbc-h2 内存数据库，不依赖 Spring。
 * 通过共享连接包装确保 H2 内存数据库跨操作数据可见。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
class R2dbcEventStoreTest {

    private CloseableConnectionFactory h2Factory;
    private EventStore eventStore;
    private Connection sharedConnection;

    @BeforeEach
    void setUp() {
        h2Factory = H2ConnectionFactory.inMemory(
                "eventstore_test_" + System.nanoTime(),
                "sa", "",
                Map.of(H2ConnectionOption.DB_CLOSE_DELAY, "-1"));
        // 创建一个共享连接，所有操作复用同一个底层连接，确保 H2 内存数据可见
        sharedConnection = Mono.from(h2Factory.create()).block();
        ConnectionFactory sharedFactory = new SingleConnectionFactory(sharedConnection);
        eventStore = new R2dbcEventStore(sharedFactory);
    }

    @AfterEach
    void tearDown() {
        try {
            if (sharedConnection != null) {
                Mono.from(sharedConnection.createStatement("DROP TABLE IF EXISTS DDD4J_EVENT_STORE").execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .block();
                Mono.from(sharedConnection.close()).block();
            }
        } catch (Exception e) {
            // 忽略清理失败
        }
    }

    @Test
    void appendThenReadShouldReturnEventsInVersionOrder() {
        String aggregateId = "order-001";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-001", "customer-001");
        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-001", "item-001", 2);
        OrderItemAddedEvent event3 = new OrderItemAddedEvent("order-001", "item-002", 1);

        eventStore.append(aggregateId, List.of(event1, event2, event3), 0);

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).version()).isEqualTo(0);
        assertThat(events.get(1).version()).isEqualTo(1);
        assertThat(events.get(2).version()).isEqualTo(2);
        assertThat(events.get(0).aggregateId()).isEqualTo("order-001");
    }

    @Test
    void appendWithWrongExpectedVersionShouldThrowAndNotPersist() {
        String aggregateId = "order-002";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-002", "customer-002");
        eventStore.append(aggregateId, List.of(event1), 0);

        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-002", "item-003", 5);

        assertThatThrownBy(() -> eventStore.append(aggregateId, List.of(event2), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Version conflict");

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).version()).isEqualTo(0);
    }

    @Test
    void readAllShouldReturnEventsInPositionOrderWithLimit() {
        String aggregateId1 = "order-010";
        String aggregateId2 = "order-020";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-010", "customer-010");
        OrderCreatedEvent event2 = new OrderCreatedEvent("order-020", "customer-020");
        OrderItemAddedEvent event3 = new OrderItemAddedEvent("order-010", "item-010", 1);

        eventStore.append(aggregateId1, List.of(event1), 0);
        eventStore.append(aggregateId2, List.of(event2), 0);
        eventStore.append(aggregateId1, List.of(event3), 1);

        List<StoredEvent> allEvents = eventStore.readAll(0, 10);
        assertThat(allEvents).hasSize(3);
        assertThat(allEvents.get(0).position()).isLessThan(allEvents.get(1).position());
        assertThat(allEvents.get(1).position()).isLessThan(allEvents.get(2).position());

        List<StoredEvent> limitedEvents = eventStore.readAll(0, 2);
        assertThat(limitedEvents).hasSize(2);

        long secondPosition = allEvents.get(1).position();
        List<StoredEvent> fromSecond = eventStore.readAll(secondPosition, 10);
        assertThat(fromSecond).hasSize(2);
        assertThat(fromSecond.get(0).position()).isEqualTo(secondPosition);
    }

    @Test
    void eventPayloadShouldSurviveRoundTrip() {
        String aggregateId = "order-030";
        OrderCreatedEvent originalEvent = new OrderCreatedEvent("order-030", "customer-030");

        eventStore.append(aggregateId, List.of(originalEvent), 0);

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(1);

        Object restoredEvent = events.get(0).event();
        assertThat(restoredEvent).isInstanceOf(OrderCreatedEvent.class);

        OrderCreatedEvent typedEvent = (OrderCreatedEvent) restoredEvent;
        assertThat(typedEvent.orderId()).isEqualTo("order-030");
        assertThat(typedEvent.customerId()).isEqualTo("customer-030");
    }

    @Test
    void multipleAggregatesShouldHaveMonotonicallyIncreasingPositions() {
        String aggregateId1 = "order-040";
        String aggregateId2 = "order-050";
        String aggregateId3 = "order-060";

        OrderCreatedEvent event1 = new OrderCreatedEvent("order-040", "customer-040");
        OrderCreatedEvent event2 = new OrderCreatedEvent("order-050", "customer-050");
        OrderCreatedEvent event3 = new OrderCreatedEvent("order-060", "customer-060");

        eventStore.append(aggregateId1, List.of(event1), 0);
        eventStore.append(aggregateId2, List.of(event2), 0);
        eventStore.append(aggregateId3, List.of(event3), 0);

        List<StoredEvent> allEvents = eventStore.readAll(0, 100);
        assertThat(allEvents).hasSize(3);

        long previousPosition = -1;
        for (StoredEvent event : allEvents) {
            assertThat(event.position()).isGreaterThan(previousPosition);
            previousPosition = event.position();
        }
    }

    // =================== 测试事件类 ===================

    record OrderCreatedEvent(String orderId, String customerId) {
    }

    record OrderItemAddedEvent(String orderId, String itemId, int quantity) {
    }

    /**
     * 单连接工厂：始终返回同一个底层连接的包装，close() 为空操作。
     * 仅用于测试——确保 H2 内存数据库跨操作数据可见。
     * 生产环境应使用连接池。
     */
    private static class SingleConnectionFactory implements ConnectionFactory {
        private final NoCloseConnection wrapper;

        SingleConnectionFactory(Connection delegate) {
            this.wrapper = new NoCloseConnection(delegate);
        }

        @Override
        public Publisher<? extends Connection> create() {
            return Mono.just(wrapper);
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "single-connection";
        }
    }

    /**
     * 包装连接：close() 为空操作，其他方法直接委托。
     */
    private static class NoCloseConnection implements Connection {
        private final Connection delegate;

        NoCloseConnection(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Publisher<Void> close() {
            return Mono.empty();
        }

        @Override
        public Publisher<Void> beginTransaction() {
            return delegate.beginTransaction();
        }

        @Override
        public Publisher<Void> beginTransaction(TransactionDefinition definition) {
            return delegate.beginTransaction(definition);
        }

        @Override
        public Publisher<Void> commitTransaction() {
            return delegate.commitTransaction();
        }

        @Override
        public Publisher<Void> rollbackTransaction() {
            return delegate.rollbackTransaction();
        }

        @Override
        public Batch createBatch() {
            return delegate.createBatch();
        }

        @Override
        public Statement createStatement(String sql) {
            return delegate.createStatement(sql);
        }

        @Override
        public Publisher<Void> createSavepoint(String name) {
            return delegate.createSavepoint(name);
        }

        @Override
        public Publisher<Void> releaseSavepoint(String name) {
            return delegate.releaseSavepoint(name);
        }

        @Override
        public Publisher<Void> rollbackTransactionToSavepoint(String name) {
            return delegate.rollbackTransactionToSavepoint(name);
        }

        @Override
        public Publisher<Void> setAutoCommit(boolean autoCommit) {
            return delegate.setAutoCommit(autoCommit);
        }

        @Override
        public boolean isAutoCommit() {
            return delegate.isAutoCommit();
        }

        @Override
        public ConnectionMetadata getMetadata() {
            return delegate.getMetadata();
        }

        @Override
        public Publisher<Void> setTransactionIsolationLevel(IsolationLevel isolationLevel) {
            return delegate.setTransactionIsolationLevel(isolationLevel);
        }

        @Override
        public IsolationLevel getTransactionIsolationLevel() {
            return delegate.getTransactionIsolationLevel();
        }

        @Override
        public Publisher<Void> setLockWaitTimeout(Duration timeout) {
            return delegate.setLockWaitTimeout(timeout);
        }

        @Override
        public Publisher<Void> setStatementTimeout(Duration timeout) {
            return delegate.setStatementTimeout(timeout);
        }

        @Override
        public Publisher<Boolean> validate(ValidationDepth depth) {
            return delegate.validate(depth);
        }
    }
}
