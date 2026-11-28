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

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String AGGREGATE_TYPE = "TestOrder";
    /** \u5c06\u6d4b\u8bd5\u9886\u57df\u4e3b\u952e\u8f6c\u6362\u4e3a\u4e8b\u4ef6\u5b58\u50a8\u6807\u8bc6\u7684\u51fd\u6570\u3002 */
    private static final Function<String, TestOrderId> ID_ADAPTER = TestOrderId::new;

    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;
    /** \u7528\u4e8e\u5f53\u524d\u8bfb\u53d6\u6216\u5199\u5165\u65ad\u8a00\u7684\u6d4b\u8bd5\u4ed3\u50a8\u3002 */
    private DefaultEventSourcingRepository<TestOrder, String> repository;

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>eventStore = new InMemoryEventStore()</code>\u3001<code>repository = new DefaultEventSourcingRepository&lt;&gt;(AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, eventStore)</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        repository = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, eventStore);
    }

    /** \u9a8c\u8bc1<code>order.hasDomainEvents()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>rebuilt.status()</code> \u7b49\u4e8e <code>"renamed"</code>\uff0c<code>rebuilt.hasDomainEvents()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>historical.status()</code> \u7b49\u4e8e <code>"created"</code>\u3002 */
    @Test
    void readWithVersionMustReplayToSpecifiedHistoricalVersion() {
        TestOrder order = new TestOrder("order-1");
        order.create();
        order.rename("renamed");
        repository.add(order);

        TestOrder historical = repository.read("order-1", 1);

        assertThat(historical.status()).isEqualTo("created");
    }

    /** \u9a8c\u8bc1<code>events.get(0).eventId()</code> \u7b49\u4e8e <code>before.get(0).eventId()</code>\uff0c<code>events.get(1).eventId()</code> \u7b49\u4e8e <code>before.get(1).eventId()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>nodeA.update(aggregateA)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.expectedVersion()</code> \u7b49\u4e8e <code>2L</code>\uff0c<code>aggregateA.hasDomainEvents()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>failingRepository.add(order)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>order.domainEvents()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
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
     * \u8bfb\u53d6 <code>order</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>order</code>
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

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("TestOrder");

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestOrderId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private String id;
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        private String status;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>TestOrder</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        public TestOrder() {
        }

        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestOrder</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        TestOrder(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
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
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        String status() {
            return status;
        }

        /** \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void create() {
            apply(new OrderCreated(new TestOrderId(id)));
        }

        /**
         * \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        void rename(String name) {
            apply(new OrderRenamed(new TestOrderId(id), name));
        }

        /** \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void archive() {
            apply(new OrderArchived(new TestOrderId(id)));
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.id = event.getEntityId().asString()</code>\u3001<code>this.status = "created"</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        private void onOrderCreated(OrderCreated event) {
            // 重放重建：聚合标识由首个事件恢复（无参构造后 id 为空）
            this.id = event.getEntityId().asString();
            this.status = "created";
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.status = event.getName()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        private void onOrderRenamed(OrderRenamed event) {
            this.status = event.getName();
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.status = "archived"</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        private void onOrderArchived(OrderArchived event) {
            this.status = "archived";
        }
    }

    /**
     * 事件样例：订单已创建。
     */
    public static final class OrderCreated extends DomainEvent<TestOrderId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreated</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderCreated() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreated</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         */
        OrderCreated(TestOrderId id) {
            super(new EntityIdPath(id));
        }
    }

    /**
     * 事件样例：订单已重命名。
     */
    public static final class OrderRenamed extends DomainEvent<TestOrderId> {

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private String name;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderRenamed</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderRenamed() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderRenamed</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        OrderRenamed(TestOrderId id, String name) {
            super(new EntityIdPath(id));
            this.name = name;
        }

        /**
         * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
         * @return <code>name</code> \u5b57\u6bb5
         */
        public String getName() {
            return name;
        }

        /**
         * \u8bbe\u7f6e<code>name</code> \u5b57\u6bb5\u3002
         * @param name \u8981\u5199\u5165\u7684\u503c
         */
        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 事件样例：订单已归档。
     */
    public static final class OrderArchived extends DomainEvent<TestOrderId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderArchived</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderArchived() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderArchived</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         */
        OrderArchived(TestOrderId id) {
            super(new EntityIdPath(id));
        }
    }
}
