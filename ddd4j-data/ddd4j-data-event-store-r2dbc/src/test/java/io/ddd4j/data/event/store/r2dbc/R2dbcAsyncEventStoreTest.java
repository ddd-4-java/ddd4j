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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String AGGREGATE_TYPE = "Order";

    /** \u4e3a\u6d4b\u8bd5 JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u5efa\u7acb H2 \u4f1a\u8bdd\u7684\u5de5\u5382\u3002 */
    private CloseableConnectionFactory h2Factory;
    /** \u6d4b\u8bd5\u5bb9\u5668\u8de8\u7528\u4f8b\u590d\u7528\u7684\u6570\u636e\u5e93\u8fde\u63a5\u3002 */
    private Connection sharedConnection;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private R2dbcAsyncEventStore eventStore;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6ce8\u518c<code>EntityIdRegistry</code>\u3002 */
    @BeforeEach
    void setUp() {
        h2Factory = H2ConnectionFactory.inMemory(
                "async_eventstore_test_" + System.nanoTime(),
                "sa", "",
                java.util.Collections.singletonMap(H2ConnectionOption.DB_CLOSE_DELAY, "-1"));
        sharedConnection = Mono.from(h2Factory.create()).block();
        eventStore = new R2dbcAsyncEventStore(new SingleConnectionFactory(sharedConnection));
        EntityIdRegistry.register(TestOrderId.TYPE_NAME, TestOrderId::new);
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6ce8\u9500<code>EntityIdRegistry</code>\u3001\u5173\u95ed<code>sharedConnection</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>restoredFirst.aggregateId()</code> \u7b49\u4e8e <code>orderId</code>\uff0c<code>restoredSecond.position()</code> \u5927\u4e8e <code>restoredFirst.position()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>error</code> \u7684\u7c7b\u578b\u4e3a <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.aggregateId()</code> \u7b49\u4e8e <code>"order-002"</code>\uff0c<code>count</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>count</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).version()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>events.get(1).version()</code> \u7b49\u4e8e <code>2</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).position()</code> \u5c0f\u4e8e <code>events.get(1).position()</code>\uff0c<code>count</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>StepVerifier.create()</code>\u3001<code>eventStore.read()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void readMissingAggregateReturnsEmpty() {
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, new TestOrderId("missing")))
                .verifyComplete();
    }

    /** \u9a8c\u8bc1<code>count</code>\u4e3a\u96f6\u3002 */
    @Test
    void appendEmptyListCompletesWithoutError() {
        StepVerifier.create(eventStore.append(AGGREGATE_TYPE, new TestOrderId("order-e"), Flux.empty(), 0))
                .verifyComplete();
        StepVerifier.create(eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-e")).count())
                .assertNext(count -> assertThat(count).isZero())
                .verifyComplete();
    }

    /** \u9a8c\u8bc1<code>error</code> \u7684\u7c7b\u578b\u4e3a <code>IllegalStateException.class</code>\uff0c<code>error.getMessage()</code> \u5305\u542b <code>"EntityIdRegistry.register"</code>\u3002 */
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

    /** \u9a8c\u8bc1\u54cd\u5e94\u5f0f\u5e8f\u5217\u4ee5 <code>IllegalArgumentException.class</code> \u5931\u8d25\u3002 */
    @Test
    void readAllLimitMustBePositive() {
        StepVerifier.create(eventStore.readAll(0, 0))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    /**
     * \u9a8c\u8bc1<code>stored.timestamp().toInstant()</code> \u7b49\u4e8e <code>expected.toInstant()</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
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

        /** \u7528\u4e8e\u6784\u9020\u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u6216\u5b9e\u4f53\u6807\u8bc6\u7684\u903b\u8f91\u7c7b\u578b\u540d\u3002 */
        private static final String TYPE_NAME = "TestOrderId";

        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestOrderId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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
            if (!(object instanceof TestOrderId)) {
                return false;
            }
            TestOrderId other = (TestOrderId) object;
            return value.equals(other.value);
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

        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        private String orderId;
        /** \u7528\u6765\u6821\u9a8c\u4ea4\u6613\u6570\u636e\u6620\u5c04\u7684\u5ba2\u6237\u6807\u8bc6\u3002 */
        private String customerId;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderCreatedEvent() {
            // Jackson 回读
            super();
        }

        /**
         * \u8bbe\u7f6e <code>orderId</code>\u3001<code>customerId</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         * @param customerId \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u7684 <code>String</code> \u503c
         */
        OrderCreatedEvent(TestOrderId orderId, String customerId) {
            super(new EntityIdPath(orderId));
            this.orderId = orderId.asString();
            this.customerId = customerId;
        }

        /**
         * \u8bfb\u53d6\u8ba2\u5355\u6807\u8bc6\u3002
         * @return \u8ba2\u5355\u6807\u8bc6
         */
        public String getOrderId() {
            return orderId;
        }

        /**
         * \u8bbe\u7f6e\u8ba2\u5355\u6807\u8bc6\u3002
         * @param orderId \u8981\u5199\u5165\u7684\u503c
         */
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        /**
         * \u8bfb\u53d6<code>customerId</code> \u5b57\u6bb5\u3002
         * @return <code>customerId</code> \u5b57\u6bb5
         */
        public String getCustomerId() {
            return customerId;
        }

        /**
         * \u8bbe\u7f6e<code>customerId</code> \u5b57\u6bb5\u3002
         * @param customerId \u8981\u5199\u5165\u7684\u503c
         */
        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    /**
     * 订单明细事件：支持 respondTo 因果链构造。
     */
    static class OrderItemAddedEvent extends DomainEvent<TestOrderId> {

        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        private String orderId;
        /** \u5b9a\u4f4d\u6279\u91cf\u8bf7\u6c42\u4e2d\u5bf9\u5e94\u6d4b\u8bd5\u9879\u7684\u6807\u8bc6\u3002 */
        private String itemId;
        /** \u6d4b\u8bd5\u8ba2\u5355\u6216\u5e93\u5b58\u4e8b\u4ef6\u643a\u5e26\u7684\u5546\u54c1\u6570\u91cf\u3002 */
        private int quantity;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderItemAddedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderItemAddedEvent() {
            // Jackson 回读
            super();
        }

        /**
         * \u8bbe\u7f6e <code>orderId</code>\u3001<code>itemId</code>\u3001<code>quantity</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>OrderItemAddedEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         * @param itemId \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderItemAddedEvent</code> \u7684 <code>String</code> \u503c
         * @param quantity \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderItemAddedEvent</code> \u7684 <code>int</code> \u503c
         */
        OrderItemAddedEvent(TestOrderId orderId, String itemId, int quantity) {
            super(new EntityIdPath(orderId));
            this.orderId = orderId.asString();
            this.itemId = itemId;
            this.quantity = quantity;
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderItemAddedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         * @param itemId \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderItemAddedEvent</code> \u7684 <code>String</code> \u503c
         * @param quantity \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderItemAddedEvent</code> \u7684 <code>int</code> \u503c
         * @param respondTo \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderItemAddedEvent</code> \u7684 <code>Event</code> \u503c
         */
        OrderItemAddedEvent(TestOrderId orderId, String itemId, int quantity, Event respondTo) {
            super(new EntityIdPath(orderId), respondTo);
            this.orderId = orderId.asString();
            this.itemId = itemId;
            this.quantity = quantity;
        }

        /**
         * \u8bfb\u53d6\u8ba2\u5355\u6807\u8bc6\u3002
         * @return \u8ba2\u5355\u6807\u8bc6
         */
        public String getOrderId() {
            return orderId;
        }

        /**
         * \u8bbe\u7f6e\u8ba2\u5355\u6807\u8bc6\u3002
         * @param orderId \u8981\u5199\u5165\u7684\u503c
         */
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        /**
         * \u8bfb\u53d6<code>itemId</code> \u5b57\u6bb5\u3002
         * @return <code>itemId</code> \u5b57\u6bb5
         */
        public String getItemId() {
            return itemId;
        }

        /**
         * \u8bbe\u7f6e<code>itemId</code> \u5b57\u6bb5\u3002
         * @param itemId \u8981\u5199\u5165\u7684\u503c
         */
        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        /**
         * \u8bfb\u53d6<code>quantity</code> \u5b57\u6bb5\u3002
         * @return <code>quantity</code> \u5b57\u6bb5
         */
        public int getQuantity() {
            return quantity;
        }

        /**
         * \u8bbe\u7f6e<code>quantity</code> \u5b57\u6bb5\u3002
         * @param quantity \u8981\u5199\u5165\u7684\u503c
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

        /** \u7528\u4e8e\u65ad\u8a00 ORM \u6761\u4ef6\u6216\u5185\u5bb9\u5305\u88c5\u7684\u5305\u88c5\u5bf9\u8c61\u3002 */
        private final NoCloseConnection wrapper;

        /**
         * \u4f7f\u7528 <code>this()</code> \u521d\u59cb\u5316 <code>SingleConnectionFactory</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param delegate \u7528\u4e8e\u521d\u59cb\u5316 <code>SingleConnectionFactory</code> \u7684 <code>Connection</code> \u503c
         */
        SingleConnectionFactory(Connection delegate) {
            this.wrapper = new NoCloseConnection(delegate);
        }

        /**
         * \u901a\u8fc7 <code>Mono.just()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Mono.just(wrapper)</code>
         */
        @Override
        public Publisher<? extends Connection> create() {
            return Mono.just(wrapper);
        }

        /**
         * \u901a\u8fc7 <code>()-&gt;"single-connection"</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>()-&gt;"single-connection"</code>
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

        /** \u4f9b\u6d4b\u8bd5\u66ff\u8eab\u8f6c\u53d1\u5b9e\u9645\u884c\u4e3a\u7684\u5e95\u5c42\u5b9e\u73b0\u3002 */
        private final Connection delegate;

        /**
         * \u8bbe\u7f6e <code>delegate</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>NoCloseConnection</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param delegate \u7528\u4e8e\u521d\u59cb\u5316 <code>NoCloseConnection</code> \u7684 <code>Connection</code> \u503c
         */
        NoCloseConnection(Connection delegate) {
            this.delegate = delegate;
        }

        /**
         * \u901a\u8fc7 <code>Mono.empty()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Mono.empty()</code>
         */
        @Override
        public Publisher<Void> close() {
            return Mono.empty();
        }

        /**
         * \u901a\u8fc7 <code>delegate.beginTransaction()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.beginTransaction()</code>
         */
        @Override
        public Publisher<Void> beginTransaction() {
            return delegate.beginTransaction();
        }

        /**
         * \u901a\u8fc7 <code>delegate.beginTransaction()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param definition \u4f20\u7ed9 <code>delegate.beginTransaction()</code> \u7684 <code>TransactionDefinition</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.beginTransaction(definition)</code>
         */
        @Override
        public Publisher<Void> beginTransaction(TransactionDefinition definition) {
            return delegate.beginTransaction(definition);
        }

        /**
         * \u901a\u8fc7 <code>delegate.commitTransaction()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.commitTransaction()</code>
         */
        @Override
        public Publisher<Void> commitTransaction() {
            return delegate.commitTransaction();
        }

        /**
         * \u901a\u8fc7 <code>delegate.rollbackTransaction()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.rollbackTransaction()</code>
         */
        @Override
        public Publisher<Void> rollbackTransaction() {
            return delegate.rollbackTransaction();
        }

        /**
         * \u901a\u8fc7 <code>delegate.createBatch()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.createBatch()</code>
         */
        @Override
        public Batch createBatch() {
            return delegate.createBatch();
        }

        /**
         * \u901a\u8fc7 <code>delegate.createStatement()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param sql \u5f53\u524d\u64cd\u4f5c\u8981\u4ea4\u7ed9\u6570\u636e\u5e93\u6267\u884c\u7684 SQL
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.createStatement(sql)</code>
         */
        @Override
        public Statement createStatement(String sql) {
            return delegate.createStatement(sql);
        }

        /**
         * \u901a\u8fc7 <code>delegate.createSavepoint()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.createSavepoint(name)</code>
         */
        @Override
        public Publisher<Void> createSavepoint(String name) {
            return delegate.createSavepoint(name);
        }

        /**
         * \u901a\u8fc7 <code>delegate.releaseSavepoint()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.releaseSavepoint(name)</code>
         */
        @Override
        public Publisher<Void> releaseSavepoint(String name) {
            return delegate.releaseSavepoint(name);
        }

        /**
         * \u901a\u8fc7 <code>delegate.rollbackTransactionToSavepoint()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.rollbackTransactionToSavepoint(name)</code>
         */
        @Override
        public Publisher<Void> rollbackTransactionToSavepoint(String name) {
            return delegate.rollbackTransactionToSavepoint(name);
        }

        /**
         * \u901a\u8fc7 <code>delegate.setAutoCommit()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param autoCommit \u4f20\u7ed9 <code>delegate.setAutoCommit()</code> \u7684 <code>boolean</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.setAutoCommit(autoCommit)</code>
         */
        @Override
        public Publisher<Void> setAutoCommit(boolean autoCommit) {
            return delegate.setAutoCommit(autoCommit);
        }

        /**
         * \u901a\u8fc7 <code>delegate.isAutoCommit()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.isAutoCommit()</code>
         */
        @Override
        public boolean isAutoCommit() {
            return delegate.isAutoCommit();
        }

        /**
         * \u901a\u8fc7 <code>delegate.getMetadata()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.getMetadata()</code>
         */
        @Override
        public ConnectionMetadata getMetadata() {
            return delegate.getMetadata();
        }

        /**
         * \u901a\u8fc7 <code>delegate.setTransactionIsolationLevel()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param isolationLevel \u4f20\u7ed9 <code>delegate.setTransactionIsolationLevel()</code> \u7684 <code>IsolationLevel</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.setTransactionIsolationLevel(isolationLevel)</code>
         */
        @Override
        public Publisher<Void> setTransactionIsolationLevel(IsolationLevel isolationLevel) {
            return delegate.setTransactionIsolationLevel(isolationLevel);
        }

        /**
         * \u901a\u8fc7 <code>delegate.getTransactionIsolationLevel()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.getTransactionIsolationLevel()</code>
         */
        @Override
        public IsolationLevel getTransactionIsolationLevel() {
            return delegate.getTransactionIsolationLevel();
        }

        /**
         * \u901a\u8fc7 <code>delegate.setLockWaitTimeout()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param timeout \u7b49\u5f85\u6d4b\u8bd5\u4e8b\u4ef6\u7684\u8d85\u65f6\u65f6\u957f
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.setLockWaitTimeout(timeout)</code>
         */
        @Override
        public Publisher<Void> setLockWaitTimeout(Duration timeout) {
            return delegate.setLockWaitTimeout(timeout);
        }

        /**
         * \u901a\u8fc7 <code>delegate.setStatementTimeout()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param timeout \u7b49\u5f85\u6d4b\u8bd5\u4e8b\u4ef6\u7684\u8d85\u65f6\u65f6\u957f
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.setStatementTimeout(timeout)</code>
         */
        @Override
        public Publisher<Void> setStatementTimeout(Duration timeout) {
            return delegate.setStatementTimeout(timeout);
        }

        /**
         * \u901a\u8fc7 <code>delegate.validate()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param depth \u4f20\u7ed9 <code>delegate.validate()</code> \u7684 <code>ValidationDepth</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.validate(depth)</code>
         */
        @Override
        public Publisher<Boolean> validate(ValidationDepth depth) {
            return delegate.validate(depth);
        }
    }
}
