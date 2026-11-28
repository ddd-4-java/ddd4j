/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DefaultEventSourcingRepository} 行为契约测试（规格：{@code event-sourcing-repository}）。
 *
 * <p>覆盖：add/update 的 append 追加语义、replay 重放重建、乐观并发冲突拒绝。
 */
class DefaultEventSourcingRepositoryMustTest {

    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String AGGREGATE_TYPE = "TestOrder";
    /**
     * 将测试领域主键转换为事件存储标识的函数。
     */
    private static final Function<String, TestOrderId> ID_ADAPTER = TestOrderId::new;

    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;
    /**
     * 用于当前读取或写入断言的测试仓储。
     */
    private DefaultEventSourcingRepository<TestOrder, String> repository;

    /**
     * 根据本次调用更新 <code>eventStore = new InMemoryEventStore()</code>、<code>repository = new DefaultEventSourcingRepository&lt;&gt;(AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, eventStore)</code>，用于后续测试步骤。
     */
    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        repository = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, eventStore);
    }

    /**
     * 验证<code>order.hasDomainEvents()</code>为假。
     */
    @Test
    void addMustAppendUncommittedEventsFromVersionOne() {
        TestOrder order = new TestOrder("order-1");
        order.create();
        order.rename("renamed");

        repository.add(order);

        List<StoredEvent> events = eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-1"));
        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L);
        // 追加语义：未提交事件被消费清空
        assertThat(order.hasDomainEvents()).isFalse();
    }

    /**
     * 验证<code>rebuilt.status()</code> 等于 <code>"renamed"</code>，<code>rebuilt.hasDomainEvents()</code>为假。
     */
    @Test
    void readMustReplayEventsToRebuildStateWithoutPendingEvents() {
        TestOrder order = new TestOrder("order-1");
        order.create();
        order.rename("renamed");
        repository.add(order);

        TestOrder rebuilt = repository.read("order-1");

        // 重放重建：状态等于两次事件依次应用后的最终状态，且不携带未提交事件
        assertThat(rebuilt.status()).isEqualTo("renamed");
        assertThat(rebuilt.hasDomainEvents()).isFalse();
    }

    /**
     * 验证<code>historical.status()</code> 等于 <code>"created"</code>。
     */
    @Test
    void readWithVersionMustReplayToSpecifiedHistoricalVersion() {
        TestOrder order = new TestOrder("order-1");
        order.create();
        order.rename("renamed");
        repository.add(order);

        TestOrder historical = repository.read("order-1", 1);

        assertThat(historical.status()).isEqualTo("created");
    }

    /**
     * 验证<code>events.get(0).eventId()</code> 等于 <code>before.get(0).eventId()</code>，<code>events.get(1).eventId()</code> 等于 <code>before.get(1).eventId()</code>。
     */
    @Test
    void updateMustAppendOnlyNewEventsPreservingExistingStream() {
        TestOrder order = new TestOrder("order-1");
        order.create();
        order.rename("renamed");
        repository.add(order);

        TestOrder loaded = repository.read("order-1");
        // 记录 update 前的既有事件标识，用于断言既有事件保持原样
        List<StoredEvent> before = eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-1"));
        loaded.archive();
        repository.update(loaded);

        List<StoredEvent> events = eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-1"));
        // 追加语义：版本 1、2 既有事件保持原样，版本 3 为新追加事件
        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L, 3L);
        assertThat(events.get(0).eventId()).isEqualTo(before.get(0).eventId());
        assertThat(events.get(1).eventId()).isEqualTo(before.get(1).eventId());
    }

    /**
     * 验证<code>nodeA.update(aggregateA)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>2L</code>，<code>aggregateA.hasDomainEvents()</code>为真。
     */
    @Test
    void concurrentUpdateWithStaleVersionMustBeRejectedWithoutPartialWrites() {
        repository.add(newOrderWithTwoEvents("order-1"));

        // 两个仓储实例（模拟两个并发节点）各自加载同一聚合
        DefaultEventSourcingRepository<TestOrder, String> nodeA = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, eventStore);
        DefaultEventSourcingRepository<TestOrder, String> nodeB = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, eventStore);
        TestOrder aggregateA = nodeA.read("order-1");
        TestOrder aggregateB = nodeB.read("order-1");

        // 实例 B 先行推进流版本 2 → 3
        aggregateB.archive();
        nodeB.update(aggregateB);

        // 实例 A 持有过期版本 2，update 必须被拒绝且事件流不出现部分写入
        aggregateA.archive();
        assertThatThrownBy(() -> nodeA.update(aggregateA))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.expectedVersion()).isEqualTo(2L);
                    assertThat(conflict.actualVersion()).isEqualTo(3L);
                });
        assertThat(eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-1")))
                .extracting(StoredEvent::version).containsExactly(1L, 2L, 3L);
        assertThat(aggregateA.hasDomainEvents()).isTrue();
    }

    /**
     * 单例仓储加载的同 ID 聚合副本必须分别保留读取版本，冲突不能清除待写事件。
     */
    @Test
    void sameRepositoryMustRejectStaleAggregateWithoutPartialWrites() {
        repository.add(newOrderWithTwoEvents("order-1"));
        TestOrder stale = repository.read("order-1");
        TestOrder current = repository.read("order-1");
        assertThat(stale).isEqualTo(current).isNotSameAs(current);
        current.rename("current");
        repository.update(current);

        stale.rename("stale");
        stale.archive();
        assertThatThrownBy(() -> repository.update(stale))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.expectedVersion()).isEqualTo(2L);
                    assertThat(conflict.actualVersion()).isEqualTo(3L);
                });
        assertThat(stale.domainEvents()).hasSize(2);
        assertThat(repository.read("order-1").status()).isEqualTo("current");
        assertThat(eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-1")))
                .extracting(StoredEvent::version).containsExactly(1L, 2L, 3L);
    }

    /**
     * 查询历史副本不能降低已经加载的最新聚合版本。
     */
    @Test
    void historicalReadMustNotInvalidateCurrentAggregate() {
        repository.add(newOrderWithTwoEvents("order-1"));
        TestOrder current = repository.read("order-1");
        repository.read("order-1", 1);

        current.archive();
        repository.update(current);

        assertThat(repository.read("order-1").status()).isEqualTo("archived");
    }

    /**
     * 读取最新版本不能把历史副本提升为可写的最新版本。
     */
    @Test
    void latestReadMustNotRefreshHistoricalAggregateVersion() {
        repository.add(newOrderWithTwoEvents("order-1"));
        TestOrder historical = repository.read("order-1", 1);
        repository.read("order-1");
        historical.archive();

        assertThatThrownBy(() -> repository.update(historical))
                .isInstanceOf(AggregateVersionConflictException.class);
        assertThat(historical.domainEvents()).hasSize(1);
        assertThat(eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-1")))
                .extracting(StoredEvent::version).containsExactly(1L, 2L);
    }

    /**
     * 新建后连续保存同一实例，版本应随每次成功追加推进。
     */
    @Test
    void successfulAppendsMustAdvanceTheSameAggregateVersion() {
        TestOrder order = newOrderWithTwoEvents("order-1");
        repository.add(order);
        order.rename("updated");
        repository.update(order);
        order.archive();
        repository.update(order);

        assertThat(order.hasDomainEvents()).isFalse();
        assertThat(repository.read("order-1").status()).isEqualTo("archived");
        assertThat(eventStore.read(AGGREGATE_TYPE, new TestOrderId("order-1")))
                .extracting(StoredEvent::version).containsExactly(1L, 2L, 3L, 4L);
    }

    /**
     * 验证<code>failingRepository.add(order)</code> 抛出 <code>IllegalStateException.class</code>，<code>order.domainEvents()</code> 的大小为 <code>1</code>。
     */
    @Test
    void failedInitialAppendMustKeepPendingEventsForRetry() {
        EventStore failingStore = new InMemoryEventStore() {
            @Override
            public synchronized void append(String type, AggregateRootId id,
                                            List<? extends DomainEvent<?>> events, long expectedVersion) {
                throw new IllegalStateException("write failed");
            }
        };
        DefaultEventSourcingRepository<TestOrder, String> failingRepository = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, failingStore);
        TestOrder order = new TestOrder("order-1");
        order.create();

        assertThatThrownBy(() -> failingRepository.add(order)).isInstanceOf(IllegalStateException.class);
        assertThat(order.domainEvents()).hasSize(1);
    }

    /**
     * 读取 <code>order</code> 供当前测试使用。
     *
     * @param id 当前要查找的测试记录标识
     * @return 当前测试使用的结果，取自 <code>order</code>
     */
    private TestOrder newOrderWithTwoEvents(String id) {
        TestOrder order = new TestOrder(id);
        order.create();
        order.rename("renamed");
        return order;
    }

    /**
     * 测试聚合根标识。
     */
    static final class TestOrderId implements AggregateRootId, Serializable {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("TestOrder");

        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestOrderId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        TestOrderId(String value) {
            this.value = Objects.requireNonNull(value, "value must not be null");
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
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
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 根据<code>value</code>、<code>TestOrderId</code>比较对象的值是否相等。
         *
         * @param o 用来比较的另一个对象，允许为空
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof TestOrderId && Objects.equals(value, ((TestOrderId) o).value));
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    /**
     * 测试聚合根：create → rename → archive 三个业务动作各产生一个事件。
     */
    static final class TestOrder extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private String id;
        /**
         * 测试替身返回、模拟或捕获的状态。
         */
        private String status;

        /**
         * 创建字段使用默认初始值的 <code>TestOrder</code> 测试对象。
         */
        public TestOrder() {
        }

        /**
         * 设置 <code>id</code>，用于构造 <code>TestOrder</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        TestOrder(String id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 按领域标识相等，验证仓储不会把不同加载实例的版本合并。
         */
        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof TestOrder && Objects.equals(id, ((TestOrder) other).id));
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        String status() {
            return status;
        }

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         */
        void create() {
            apply(new OrderCreated(new TestOrderId(id)));
        }

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        void rename(String name) {
            apply(new OrderRenamed(new TestOrderId(id), name));
        }

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         */
        void archive() {
            apply(new OrderArchived(new TestOrderId(id)));
        }

        /**
         * 根据本次调用更新 <code>this.id = event.getEntityId().asString()</code>、<code>this.status = "created"</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        private void onOrderCreated(OrderCreated event) {
            // 重放重建：聚合标识由首个事件恢复（无参构造后 id 为空）
            this.id = event.getEntityId().asString();
            this.status = "created";
        }

        /**
         * 根据本次调用更新 <code>this.status = event.getName()</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        private void onOrderRenamed(OrderRenamed event) {
            this.status = event.getName();
        }

        /**
         * 根据本次调用更新 <code>this.status = "archived"</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        private void onOrderArchived(OrderArchived event) {
            this.status = "archived";
        }
    }

    /**
     * 事件样例：订单已创建。
     */
    public static final class OrderCreated extends DomainEvent<TestOrderId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreated</code> 测试夹具。
         */
        public OrderCreated() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreated</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        OrderCreated(TestOrderId id) {
            super(new EntityIdPath(id));
        }
    }

    /**
     * 事件样例：订单已重命名。
     */
    public static final class OrderRenamed extends DomainEvent<TestOrderId> {

        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private String name;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderRenamed</code> 测试夹具。
         */
        public OrderRenamed() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderRenamed</code> 测试夹具。
         *
         * @param id   当前要查找的测试记录标识
         * @param name 当前测试指定的对象或属性名称
         */
        OrderRenamed(TestOrderId id, String name) {
            super(new EntityIdPath(id));
            this.name = name;
        }

        /**
         * 读取<code>name</code> 字段。
         *
         * @return <code>name</code> 字段
         */
        public String getName() {
            return name;
        }

        /**
         * 设置<code>name</code> 字段。
         *
         * @param name 要写入的值
         */
        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 事件样例：订单已归档。
     */
    public static final class OrderArchived extends DomainEvent<TestOrderId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderArchived</code> 测试夹具。
         */
        public OrderArchived() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderArchived</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        OrderArchived(TestOrderId id) {
            super(new EntityIdPath(id));
        }
    }
}
