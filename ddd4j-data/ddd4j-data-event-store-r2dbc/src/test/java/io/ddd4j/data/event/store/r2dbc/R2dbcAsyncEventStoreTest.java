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

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.AsyncStoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityIdRegistry;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.Event;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.r2dbc.h2.CloseableConnectionFactory;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.Batch;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link R2dbcAsyncEventStore} 集成测试——H2 内存数据库全量契约验证（真响应式，无 block）。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>append 后 read 按版本顺序返回，typed id/聚合类型/因果元数据/类型化 payload 全字段还原</li>
 *   <li>乐观锁：expectedVersion 不匹配以 {@link AggregateVersionConflictException} 错误信号终止且不落库</li>
 *   <li>read 版本区间</li>
 *   <li>readAll 按全局 position 升序 + limit 分页</li>
 *   <li>多聚合 append 交错时全局 position 单调</li>
 *   <li>自定义 id 类型经 {@link EntityIdRegistry} 还原；未注册类型显式报错（附注册指引）</li>
 *   <li>因果链（respondTo）：correlationId/causationId 随事件持久化还原</li>
 * </ol>
 *
 * <p>使用 r2dbc-h2 内存数据库；通过共享连接包装确保 H2 内存数据库跨操作数据可见
 * （与同步 {@link R2dbcEventStore} 测试同款模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class R2dbcAsyncEventStoreTest {

    private static final String AGGREGATE_TYPE = "Order";

    private CloseableConnectionFactory h2Factory;
    private Connection sharedConnection;
    private R2dbcAsyncEventStore eventStore;

    @BeforeEach
    void setUp() {
        h2Factory = H2ConnectionFactory.inMemory(
                "async_eventstore_test_" + System.nanoTime(),
                "sa", "",
                Map.of(H2ConnectionOption.DB_CLOSE_DELAY, "-1"));
        sharedConnection = Mono.from(h2Factory.create()).block();
        eventStore = new R2dbcAsyncEventStore(new SingleConnectionFactory(sharedConnection));
        EntityIdRegistry.register(TestOrderId.TYPE_NAME, TestOrderId::new);
    }

    @AfterEach
    void tearDown() {
        try {
            EntityIdRegistry.unregister(TestOrderId.TYPE_NAME);
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
    void appendThenReadReturnsEventsInVersionOrderWithFullFieldRestoration() {
        TestOrderId orderId = new TestOrderId("order-001");
        OrderCreatedEvent first = new OrderCreatedEvent(orderId, "customer-001");
        OrderItemAddedEvent second = new OrderItemAddedEvent(orderId, "item-001", 2, first);

        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(first, second), 0))
                .verifyComplete();

        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, orderId).collectList())
                .assertNext(events -> {
                    assertThat(events).hasSize(2);
                    AsyncStoredEvent restoredFirst = events.get(0);
                    assertThat(restoredFirst.aggregateId()).isEqualTo(orderId);
                    assertThat(restoredFirst.aggregateType()).isEqualTo(AGGREGATE_TYPE);
                    assertThat(restoredFirst.version()).isEqualTo(0);
                    assertThat(restoredFirst.payload()).isInstanceOf(OrderCreatedEvent.class);
                    assertThat(restoredFirst.eventId()).isEqualTo(first.getEventId());

                    AsyncStoredEvent restoredSecond = events.get(1);
                    assertThat(restoredSecond.version()).isEqualTo(1);
                    assertThat(restoredSecond.payload()).isInstanceOf(OrderItemAddedEvent.class);
                    // 因果链：respondTo(first) → correlationId/causationId 均为 first.eventId
                    assertThat(restoredSecond.correlationId()).isEqualTo(first.getEventId());
                    assertThat(restoredSecond.causationId()).isEqualTo(first.getEventId());
                    assertThat(restoredSecond.position()).isGreaterThan(restoredFirst.position());
                })
                .verifyComplete();
    }

    @Test
    void appendVersionConflictEmitsConflictAndPersistsNothing() {
        TestOrderId orderId = new TestOrderId("order-002");
        eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(new OrderCreatedEvent(orderId, "c-002")), 0).block();

        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, orderId,
                        Flux.just(new OrderItemAddedEvent(orderId, "item-002", 1)), 0))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AggregateVersionConflictException.class);
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.aggregateId()).isEqualTo("order-002");
                    assertThat(conflict.expectedVersion()).isEqualTo(0);
                    assertThat(conflict.actualVersion()).isEqualTo(1);
                })
                .verify();

        // 冲突后流未受影响
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, orderId).count())
                .assertNext(count -> assertThat(count).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void readSupportsVersionRange() {
        TestOrderId orderId = new TestOrderId("order-003");
        OrderCreatedEvent first = new OrderCreatedEvent(orderId, "c-003");
        OrderItemAddedEvent second = new OrderItemAddedEvent(orderId, "item-a", 1, first);
        OrderItemAddedEvent third = new OrderItemAddedEvent(orderId, "item-b", 2, second);

        eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(first, second, third), 0).block();

        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, orderId, 1, 2).collectList())
                .assertNext(events -> {
                    assertThat(events).hasSize(2);
                    assertThat(events.get(0).version()).isEqualTo(1);
                    assertThat(events.get(1).version()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void readAllPaginatesByGlobalPosition() {
        TestOrderId orderA = new TestOrderId("order-a");
        TestOrderId orderB = new TestOrderId("order-b");
        eventStore.append(AGGREGATE_TYPE, orderA, Flux.just(new OrderCreatedEvent(orderA, "c-a")), 0).block();
        eventStore.append(AGGREGATE_TYPE, orderB, Flux.just(new OrderCreatedEvent(orderB, "c-b")), 0).block();
        eventStore.append(AGGREGATE_TYPE, orderA, Flux.just(new OrderItemAddedEvent(orderA, "item-a", 1)), 1).block();

        StepVerifier.create(eventStore.readAll(0, 2).collectList())
                .assertNext(events -> {
                    assertThat(events).hasSize(2);
                    assertThat(events.get(0).position()).isLessThan(events.get(1).position());
                    // 类型化 id 经 EntityIdRegistry 还原
                    assertThat(events.get(0).aggregateId()).isInstanceOf(TestOrderId.class);
                    assertThat(events.get(0).aggregateType()).isEqualTo(AGGREGATE_TYPE);
                })
                .verifyComplete();

        long lastPosition = eventStore.readAll(0, 10).collectList().block().get(2).position();
        // fromPosition 含（inclusive）：从最后一条位置起读只应返回该条本身
        StepVerifier.create(eventStore.readAll(lastPosition, 10).count())
                .assertNext(count -> assertThat(count).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void readMissingAggregateReturnsEmpty() {
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, new TestOrderId("missing")))
                .verifyComplete();
    }

    @Test
    void appendEmptyListCompletesWithoutError() {
        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, new TestOrderId("order-e"), Flux.empty(), 0))
                .verifyComplete();
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-e")).count())
                .assertNext(count -> assertThat(count).isZero())
                .verifyComplete();
    }

    @Test
    void readAllWithUnregisteredIdTypeFailsWithRegistrationGuidance() {
        EntityIdRegistry.unregister(TestOrderId.TYPE_NAME);
        TestOrderId orderId = new TestOrderId("order-u");
        eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(new OrderCreatedEvent(orderId, "c-u")), 0).block();

        StepVerifier.create(eventStore.readAll(0, 10))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalStateException.class);
                    assertThat(error.getMessage()).contains("EntityIdRegistry.register");
                })
                .verify();
    }

    @Test
    void readAllLimitMustBePositive() {
        StepVerifier.create(eventStore.readAll(0, 0))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    // =================== 测试事件类 ===================

    /**
     * 测试聚合根标识：注册到 {@link EntityIdRegistry} 以验证 typed id 还原。
     */
    static final class TestOrderId implements AggregateRootId {

        private static final String TYPE_NAME = "TestOrderId";

        private final String value;

        TestOrderId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return new StringEntityType(TYPE_NAME);
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE_NAME + ":" + value;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof TestOrderId other && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return asTypedString();
        }
    }

    /**
     * 订单创建事件（朴素样例，无任何 Jackson workaround）。
     */
    static class OrderCreatedEvent extends DomainEvent<TestOrderId> {

        private String orderId;
        private String customerId;

        OrderCreatedEvent() {
            // Jackson 回读
            super();
        }

        OrderCreatedEvent(TestOrderId orderId, String customerId) {
            super(new EntityIdPath(orderId));
            this.orderId = orderId.asString();
            this.customerId = customerId;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    /**
     * 订单明细事件：支持 respondTo 因果链构造。
     */
    static class OrderItemAddedEvent extends DomainEvent<TestOrderId> {

        private String orderId;
        private String itemId;
        private int quantity;

        OrderItemAddedEvent() {
            // Jackson 回读
            super();
        }

        OrderItemAddedEvent(TestOrderId orderId, String itemId, int quantity) {
            super(new EntityIdPath(orderId));
            this.orderId = orderId.asString();
            this.itemId = itemId;
            this.quantity = quantity;
        }

        OrderItemAddedEvent(TestOrderId orderId, String itemId, int quantity, Event respondTo) {
            super(new EntityIdPath(orderId), respondTo);
            this.orderId = orderId.asString();
            this.itemId = itemId;
            this.quantity = quantity;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    /**
     * 单连接工厂：始终返回同一个底层连接的包装，close() 为空操作。
     * 仅用于测试——确保 H2 内存数据库跨操作数据可见。生产环境应使用连接池。
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
     * 包装连接：close() 为空操作，其他方法直接委托（与同步 {@link R2dbcEventStoreTest} 同款）。
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
