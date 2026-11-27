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

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.AsyncStoredEvent;
import io.ddd4j.core.ddd.event.*;
import io.r2dbc.h2.CloseableConnectionFactory;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.h2.H2ConnectionOption;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String AGGREGATE_TYPE = "Order";

    /**
     * 为测试 JPA 实体管理器建立 H2 会话的工厂。
     */
    private CloseableConnectionFactory h2Factory;
    /**
     * 测试容器跨用例复用的数据库连接。
     */
    private Connection sharedConnection;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private R2dbcAsyncEventStore eventStore;

    /**
     * 测试前依次注册<code>EntityIdRegistry</code>。
     */
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

    /**
     * 测试后依次注销<code>EntityIdRegistry</code>、关闭<code>sharedConnection</code>。
     */
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

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>restoredFirst.aggregateId()</code> 等于 <code>orderId</code>，<code>restoredSecond.position()</code> 大于 <code>restoredFirst.position()</code>。
     */
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
                    assertThat(restoredFirst.version()).isEqualTo(1);
                    assertThat(restoredFirst.payload()).isInstanceOf(OrderCreatedEvent.class);
                    assertThat(restoredFirst.eventId()).isEqualTo(first.getEventId());

                    AsyncStoredEvent restoredSecond = events.get(1);
                    assertThat(restoredSecond.version()).isEqualTo(2);
                    assertThat(restoredSecond.payload()).isInstanceOf(OrderItemAddedEvent.class);
                    // 因果链：respondTo(first) → correlationId/causationId 均为 first.eventId
                    assertThat(restoredSecond.correlationId()).isEqualTo(first.getEventId());
                    assertThat(restoredSecond.causationId()).isEqualTo(first.getEventId());
                    assertThat(restoredSecond.position()).isGreaterThan(restoredFirst.position());
                })
                .verifyComplete();
    }

    /**
     * 验证<code>error</code> 的类型为 <code>AggregateVersionConflictException.class</code>，<code>conflict.aggregateId()</code> 等于 <code>"order-002"</code>，<code>count</code> 等于 <code>1</code>。
     */
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

    /**
     * 验证<code>error</code> 的异常消息包含 <code>"CK_ASYNC_BATCH_VERSION"</code>。
     */
    @Test
    void secondDatabaseInsertFailureRollsBackBatchAndAllowsVersionRetry() {
        TestOrderId orderId = new TestOrderId("order-failed-batch");
        OrderCreatedEvent committed = new OrderCreatedEvent(orderId, "customer-batch");
        OrderItemAddedEvent first = new OrderItemAddedEvent(orderId, "valid-item", 1);
        OrderItemAddedEvent second = new OrderItemAddedEvent(orderId, "rejected-item", 1);

        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(committed), 0))
                .verifyComplete();

        // 只拒绝批次的第二条 INSERT，第一条仍会进入同一数据库事务。
        String rejectSecondInsert = "ALTER TABLE " + EventStoreConstants.TABLE_NAME
                + " ADD CONSTRAINT CK_ASYNC_BATCH_VERSION CHECK (" + EventStoreConstants.COLUMN_VERSION + " <> 3)";
        StepVerifier.create(Mono.from(sharedConnection.createStatement(rejectSecondInsert).execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .then())
                .verifyComplete();

        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(first, second), 1))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("CK_ASYNC_BATCH_VERSION"))
                .verify();

        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, orderId).map(AsyncStoredEvent::eventId))
                .expectNext(committed.getEventId())
                .verifyComplete();
        StepVerifier.create(eventStore.readAll(1, 10).map(AsyncStoredEvent::position))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(first), 1))
                .verifyComplete();
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, orderId).map(AsyncStoredEvent::eventId))
                .expectNext(committed.getEventId(), first.getEventId())
                .verifyComplete();
        StepVerifier.create(eventStore.readAll(1, 10).map(AsyncStoredEvent::position))
                .expectNext(1L, 2L)
                .verifyComplete();
    }

    /**
     * 验证<code>Mono.fromFuture(rowsBeforeCommit).block(timeout)</code> 等于 <code>3L</code>。
     */
    @Test
    void cancellationBeforeCommitRollsBackWrittenBatchAndAllowsVersionRetry() {
        Duration timeout = Duration.ofSeconds(5);
        TestOrderId orderId = new TestOrderId("order-cancelled-batch");
        OrderCreatedEvent committed = new OrderCreatedEvent(orderId, "customer-batch");
        OrderItemAddedEvent first = new OrderItemAddedEvent(orderId, "first-item", 1);
        OrderItemAddedEvent second = new OrderItemAddedEvent(orderId, "second-item", 1);
        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(committed), 0))
                .expectComplete().verify(timeout);

        CompletableFuture<Long> rowsBeforeCommit = new CompletableFuture<>();
        CompletableFuture<Void> connectionReleased = new CompletableFuture<>();
        R2dbcAsyncEventStore cancellableStore = new R2dbcAsyncEventStore(
                new SingleConnectionFactory(new NoCloseConnection(sharedConnection) {
                    @Override
                    public Publisher<Void> commitTransaction() {
                        // 用真实事务查询确认两条 INSERT 已完成，在提交前停住。
                        return Mono.from(sharedConnection.createStatement(
                                        "SELECT COUNT(*) FROM " + EventStoreConstants.TABLE_NAME).execute())
                                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get(0, Long.class))))
                                .doOnNext(rowsBeforeCommit::complete)
                                .then(Mono.never());
                    }

                    @Override
                    public Publisher<Void> close() {
                        return Mono.fromRunnable(() -> {
                            if (rowsBeforeCommit.isDone()) {
                                connectionReleased.complete(null);
                            }
                        });
                    }
                }));

        StepVerifier.create(cancellableStore.append(AGGREGATE_TYPE, orderId, Flux.just(first, second), 1))
                .then(() -> assertThat(Mono.fromFuture(rowsBeforeCommit).block(timeout)).isEqualTo(3L))
                .thenCancel().verify(timeout);
        Mono.fromFuture(connectionReleased).block(timeout);

        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, orderId).map(AsyncStoredEvent::eventId))
                .expectNext(committed.getEventId())
                .expectComplete().verify(timeout);

        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(first, second), 1))
                .expectComplete().verify(timeout);
        StepVerifier.create(eventStore.readAll(1, 10).map(AsyncStoredEvent::position))
                .expectNext(1L, 2L, 3L)
                .expectComplete().verify(timeout);
    }

    /**
     * 验证<code>count</code> 等于 <code>1</code>。
     */
    @Test
    void streamsWithSameIdAndDifferentAggregateTypesMustRemainIndependent() {
        TestOrderId sharedId = new TestOrderId("shared-id");

        StepVerifier.create(eventStore.append("Order", sharedId,
                        Flux.just(new OrderCreatedEvent(sharedId, "order-customer")), 0))
                .verifyComplete();
        StepVerifier.create(eventStore.append("Invoice", sharedId,
                        Flux.just(new OrderCreatedEvent(sharedId, "invoice-customer")), 0))
                .verifyComplete();

        StepVerifier.create(eventStore.read("Order", sharedId).count())
                .assertNext(count -> assertThat(count).isEqualTo(1))
                .verifyComplete();
        StepVerifier.create(eventStore.read("Invoice", sharedId).count())
                .assertNext(count -> assertThat(count).isEqualTo(1))
                .verifyComplete();
    }

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).version()</code> 等于 <code>1</code>，<code>events.get(1).version()</code> 等于 <code>2</code>。
     */
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

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).position()</code> 小于 <code>events.get(1).position()</code>，<code>count</code> 等于 <code>1</code>。
     */
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

    /**
     * 在测试框架中执行 <code>StepVerifier.create()</code>、<code>eventStore.read()</code>，验证该场景。
     */
    @Test
    void readMissingAggregateReturnsEmpty() {
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, new TestOrderId("missing")))
                .verifyComplete();
    }

    /**
     * 验证<code>count</code>为零。
     */
    @Test
    void appendEmptyListCompletesWithoutError() {
        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, new TestOrderId("order-e"), Flux.empty(), 0))
                .verifyComplete();
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-e")).count())
                .assertNext(count -> assertThat(count).isZero())
                .verifyComplete();
    }

    /**
     * 验证<code>error</code> 的类型为 <code>IllegalStateException.class</code>，<code>error.getMessage()</code> 包含 <code>"EntityIdRegistry.register"</code>。
     */
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

    /**
     * 验证响应式序列以 <code>IllegalArgumentException.class</code> 失败。
     */
    @Test
    void readAllLimitMustBePositive() {
        StepVerifier.create(eventStore.readAll(0, 0))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    /**
     * 验证<code>stored.timestamp().toInstant()</code> 等于 <code>expected.toInstant()</code>。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void persistedTimestampMustComeFromEvent() throws Exception {
        TestOrderId orderId = new TestOrderId("order-time");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, "customer-time");
        java.time.ZonedDateTime expected = java.time.ZonedDateTime.parse("2024-01-02T03:04:05Z");
        java.lang.reflect.Field field = DomainEvent.class.getDeclaredField("eventTimestamp");
        field.setAccessible(true);
        field.set(event, expected);

        eventStore.append(AGGREGATE_TYPE, orderId, Flux.just(event), 0).block();

        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, orderId).next())
                .assertNext(stored -> assertThat(stored.timestamp().toInstant()).isEqualTo(expected.toInstant()))
                .verifyComplete();
    }

    // =================== 测试事件类 ===================

    /**
     * 测试聚合根标识：注册到 {@link EntityIdRegistry} 以验证 typed id 还原。
     */
    static final class TestOrderId implements AggregateRootId {

        /**
         * 用于构造测试事件流或实体标识的逻辑类型名。
         */
        private static final String TYPE_NAME = "TestOrderId";

        /**
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestOrderId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
         */
        TestOrderId(String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 此操作产生的<code>EntityType</code>结果
         */
        @Override
        public EntityType getType() {
            return new StringEntityType(TYPE_NAME);
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * @return 实际返回<code>TYPE_NAME + ":" + value</code>，供调用方取得当前值
         */
        @Override
        public String asTypedString() {
            return TYPE_NAME + ":" + value;
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * @param object 用于此操作的 <code>object</code>（<code>Object</code>）
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object object) {
            return object instanceof TestOrderId other && value.equals(other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return value.hashCode();
        }

        /**
         * 生成用于调试和日志的对象描述。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return asTypedString();
        }
    }

    /**
     * 订单创建事件（朴素样例，无任何 Jackson workaround）。
     */
    static class OrderCreatedEvent extends DomainEvent<TestOrderId> {

        /**
         * 订单标识。
         */
        private String orderId;
        /**
         * 用来校验交易数据映射的客户标识。
         */
        private String customerId;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        OrderCreatedEvent() {
            // Jackson 回读
            super();
        }

        /**
         * 设置 <code>orderId</code>、<code>customerId</code>，用于构造 <code>OrderCreatedEvent</code> 测试实例。
         *
         * @param orderId    订单标识
         * @param customerId 用于初始化 <code>OrderCreatedEvent</code> 的 <code>String</code> 值
         */
        OrderCreatedEvent(TestOrderId orderId, String customerId) {
            super(new EntityIdPath(orderId));
            this.orderId = orderId.asString();
            this.customerId = customerId;
        }

        /**
         * 读取订单标识。
         *
         * @return 订单标识
         */
        public String getOrderId() {
            return orderId;
        }

        /**
         * 设置订单标识。
         *
         * @param orderId 要写入的值
         */
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        /**
         * 读取<code>customerId</code> 字段。
         *
         * @return <code>customerId</code> 字段
         */
        public String getCustomerId() {
            return customerId;
        }

        /**
         * 设置<code>customerId</code> 字段。
         *
         * @param customerId 要写入的值
         */
        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    /**
     * 订单明细事件：支持 respondTo 因果链构造。
     */
    static class OrderItemAddedEvent extends DomainEvent<TestOrderId> {

        /**
         * 订单标识。
         */
        private String orderId;
        /**
         * 定位批量请求中对应测试项的标识。
         */
        private String itemId;
        /**
         * 测试订单或库存事件携带的商品数量。
         */
        private int quantity;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderItemAddedEvent</code> 测试夹具。
         */
        OrderItemAddedEvent() {
            // Jackson 回读
            super();
        }

        /**
         * 设置 <code>orderId</code>、<code>itemId</code>、<code>quantity</code>，用于构造 <code>OrderItemAddedEvent</code> 测试实例。
         *
         * @param orderId  订单标识
         * @param itemId   用于初始化 <code>OrderItemAddedEvent</code> 的 <code>String</code> 值
         * @param quantity 用于初始化 <code>OrderItemAddedEvent</code> 的 <code>int</code> 值
         */
        OrderItemAddedEvent(TestOrderId orderId, String itemId, int quantity) {
            super(new EntityIdPath(orderId));
            this.orderId = orderId.asString();
            this.itemId = itemId;
            this.quantity = quantity;
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderItemAddedEvent</code> 测试夹具。
         *
         * @param orderId   订单标识
         * @param itemId    用于初始化 <code>OrderItemAddedEvent</code> 的 <code>String</code> 值
         * @param quantity  用于初始化 <code>OrderItemAddedEvent</code> 的 <code>int</code> 值
         * @param respondTo 用于初始化 <code>OrderItemAddedEvent</code> 的 <code>Event</code> 值
         */
        OrderItemAddedEvent(TestOrderId orderId, String itemId, int quantity, Event respondTo) {
            super(new EntityIdPath(orderId), respondTo);
            this.orderId = orderId.asString();
            this.itemId = itemId;
            this.quantity = quantity;
        }

        /**
         * 读取订单标识。
         *
         * @return 订单标识
         */
        public String getOrderId() {
            return orderId;
        }

        /**
         * 设置订单标识。
         *
         * @param orderId 要写入的值
         */
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        /**
         * 读取<code>itemId</code> 字段。
         *
         * @return <code>itemId</code> 字段
         */
        public String getItemId() {
            return itemId;
        }

        /**
         * 设置<code>itemId</code> 字段。
         *
         * @param itemId 要写入的值
         */
        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        /**
         * 读取<code>quantity</code> 字段。
         *
         * @return <code>quantity</code> 字段
         */
        public int getQuantity() {
            return quantity;
        }

        /**
         * 设置<code>quantity</code> 字段。
         *
         * @param quantity 要写入的值
         */
        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    /**
     * 单连接工厂：始终返回同一个底层连接的包装，close() 为空操作。
     * 仅用于测试——确保 H2 内存数据库跨操作数据可见。生产环境应使用连接池。
     */
    private static class SingleConnectionFactory implements ConnectionFactory {

        /**
         * 用于断言 ORM 条件或内容包装的包装对象。
         */
        private final NoCloseConnection wrapper;

        /**
         * 使用 <code>this()</code> 初始化 <code>SingleConnectionFactory</code> 测试夹具。
         *
         * @param delegate 用于初始化 <code>SingleConnectionFactory</code> 的 <code>Connection</code> 值
         */
        SingleConnectionFactory(Connection delegate) {
            this(new NoCloseConnection(delegate));
        }

        /**
         * 设置 <code>wrapper</code>，用于构造 <code>SingleConnectionFactory</code> 测试实例。
         *
         * @param wrapper 用于初始化 <code>SingleConnectionFactory</code> 的 <code>NoCloseConnection</code> 值
         */
        SingleConnectionFactory(NoCloseConnection wrapper) {
            this.wrapper = wrapper;
        }

        /**
         * 通过 <code>Mono.just()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Mono.just(wrapper)</code>
         */
        @Override
        public Publisher<? extends Connection> create() {
            return Mono.just(wrapper);
        }

        /**
         * 通过 <code>()-&gt;"single-connection"</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>()-&gt;"single-connection"</code>
         */
        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "single-connection";
        }
    }

    /**
     * 包装连接：close() 为空操作，其他方法直接委托（与同步 {@link R2dbcEventStoreTest} 同款）。
     */
    private static class NoCloseConnection implements Connection {

        /**
         * 供测试替身转发实际行为的底层实现。
         */
        private final Connection delegate;

        /**
         * 设置 <code>delegate</code>，用于构造 <code>NoCloseConnection</code> 测试实例。
         *
         * @param delegate 用于初始化 <code>NoCloseConnection</code> 的 <code>Connection</code> 值
         */
        NoCloseConnection(Connection delegate) {
            this.delegate = delegate;
        }

        /**
         * 通过 <code>Mono.empty()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Mono.empty()</code>
         */
        @Override
        public Publisher<Void> close() {
            return Mono.empty();
        }

        /**
         * 通过 <code>delegate.beginTransaction()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.beginTransaction()</code>
         */
        @Override
        public Publisher<Void> beginTransaction() {
            return delegate.beginTransaction();
        }

        /**
         * 通过 <code>delegate.beginTransaction()</code> 取得当前测试需要的结果。
         *
         * @param definition 传给 <code>delegate.beginTransaction()</code> 的 <code>TransactionDefinition</code> 输入
         * @return 当前测试使用的结果，取自 <code>delegate.beginTransaction(definition)</code>
         */
        @Override
        public Publisher<Void> beginTransaction(TransactionDefinition definition) {
            return delegate.beginTransaction(definition);
        }

        /**
         * 通过 <code>delegate.commitTransaction()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.commitTransaction()</code>
         */
        @Override
        public Publisher<Void> commitTransaction() {
            return delegate.commitTransaction();
        }

        /**
         * 通过 <code>delegate.rollbackTransaction()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.rollbackTransaction()</code>
         */
        @Override
        public Publisher<Void> rollbackTransaction() {
            return delegate.rollbackTransaction();
        }

        /**
         * 通过 <code>delegate.createBatch()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.createBatch()</code>
         */
        @Override
        public Batch createBatch() {
            return delegate.createBatch();
        }

        /**
         * 通过 <code>delegate.createStatement()</code> 取得当前测试需要的结果。
         *
         * @param sql 当前操作要交给数据库执行的 SQL
         * @return 当前测试使用的结果，取自 <code>delegate.createStatement(sql)</code>
         */
        @Override
        public Statement createStatement(String sql) {
            return delegate.createStatement(sql);
        }

        /**
         * 通过 <code>delegate.createSavepoint()</code> 取得当前测试需要的结果。
         *
         * @param name 当前测试指定的对象或属性名称
         * @return 当前测试使用的结果，取自 <code>delegate.createSavepoint(name)</code>
         */
        @Override
        public Publisher<Void> createSavepoint(String name) {
            return delegate.createSavepoint(name);
        }

        /**
         * 通过 <code>delegate.releaseSavepoint()</code> 取得当前测试需要的结果。
         *
         * @param name 当前测试指定的对象或属性名称
         * @return 当前测试使用的结果，取自 <code>delegate.releaseSavepoint(name)</code>
         */
        @Override
        public Publisher<Void> releaseSavepoint(String name) {
            return delegate.releaseSavepoint(name);
        }

        /**
         * 通过 <code>delegate.rollbackTransactionToSavepoint()</code> 取得当前测试需要的结果。
         *
         * @param name 当前测试指定的对象或属性名称
         * @return 当前测试使用的结果，取自 <code>delegate.rollbackTransactionToSavepoint(name)</code>
         */
        @Override
        public Publisher<Void> rollbackTransactionToSavepoint(String name) {
            return delegate.rollbackTransactionToSavepoint(name);
        }

        /**
         * 通过 <code>delegate.setAutoCommit()</code> 取得当前测试需要的结果。
         *
         * @param autoCommit 传给 <code>delegate.setAutoCommit()</code> 的 <code>boolean</code> 输入
         * @return 当前测试使用的结果，取自 <code>delegate.setAutoCommit(autoCommit)</code>
         */
        @Override
        public Publisher<Void> setAutoCommit(boolean autoCommit) {
            return delegate.setAutoCommit(autoCommit);
        }

        /**
         * 通过 <code>delegate.isAutoCommit()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.isAutoCommit()</code>
         */
        @Override
        public boolean isAutoCommit() {
            return delegate.isAutoCommit();
        }

        /**
         * 通过 <code>delegate.getMetadata()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.getMetadata()</code>
         */
        @Override
        public ConnectionMetadata getMetadata() {
            return delegate.getMetadata();
        }

        /**
         * 通过 <code>delegate.setTransactionIsolationLevel()</code> 取得当前测试需要的结果。
         *
         * @param isolationLevel 传给 <code>delegate.setTransactionIsolationLevel()</code> 的 <code>IsolationLevel</code> 输入
         * @return 当前测试使用的结果，取自 <code>delegate.setTransactionIsolationLevel(isolationLevel)</code>
         */
        @Override
        public Publisher<Void> setTransactionIsolationLevel(IsolationLevel isolationLevel) {
            return delegate.setTransactionIsolationLevel(isolationLevel);
        }

        /**
         * 通过 <code>delegate.getTransactionIsolationLevel()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.getTransactionIsolationLevel()</code>
         */
        @Override
        public IsolationLevel getTransactionIsolationLevel() {
            return delegate.getTransactionIsolationLevel();
        }

        /**
         * 通过 <code>delegate.setLockWaitTimeout()</code> 取得当前测试需要的结果。
         *
         * @param timeout 等待测试事件的超时时长
         * @return 当前测试使用的结果，取自 <code>delegate.setLockWaitTimeout(timeout)</code>
         */
        @Override
        public Publisher<Void> setLockWaitTimeout(Duration timeout) {
            return delegate.setLockWaitTimeout(timeout);
        }

        /**
         * 通过 <code>delegate.setStatementTimeout()</code> 取得当前测试需要的结果。
         *
         * @param timeout 等待测试事件的超时时长
         * @return 当前测试使用的结果，取自 <code>delegate.setStatementTimeout(timeout)</code>
         */
        @Override
        public Publisher<Void> setStatementTimeout(Duration timeout) {
            return delegate.setStatementTimeout(timeout);
        }

        /**
         * 通过 <code>delegate.validate()</code> 取得当前测试需要的结果。
         *
         * @param depth 传给 <code>delegate.validate()</code> 的 <code>ValidationDepth</code> 输入
         * @return 当前测试使用的结果，取自 <code>delegate.validate(depth)</code>
         */
        @Override
        public Publisher<Boolean> validate(ValidationDepth depth) {
            return delegate.validate(depth);
        }
    }
}
