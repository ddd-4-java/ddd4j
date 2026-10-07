/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
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

    private static final String AGGREGATE_TYPE = "TestOrder";
    private static final Function<String, TestOrderId> ID_ADAPTER = TestOrderId::new;

    private EventStore eventStore;
    private DefaultEventSourcingRepository<TestOrder, String> repository;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore();
        repository = new DefaultEventSourcingRepository<>(
                AGGREGATE_TYPE, TestOrder.class, ID_ADAPTER, eventStore);
    }

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

    @Test
    void readWithVersionMustReplayToSpecifiedHistoricalVersion() {
        TestOrder order = new TestOrder("order-1");
        order.create();
        order.rename("renamed");
        repository.add(order);

        TestOrder historical = repository.read("order-1", 1);

        assertThat(historical.status()).isEqualTo("created");
    }

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
    }

    private TestOrder newOrderWithTwoEvents(String id) {
        TestOrder order = new TestOrder(id);
        order.create();
        order.rename("renamed");
        return order;
    }

    /** 测试聚合根标识。 */
    static final class TestOrderId implements AggregateRootId, Serializable {

        private static final EntityType TYPE = new StringEntityType("TestOrder");

        private final String value;

        TestOrderId(String value) {
            this.value = Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof TestOrderId && Objects.equals(value, ((TestOrderId) o).value));
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    /** 测试聚合根：create → rename → archive 三个业务动作各产生一个事件。 */
    static final class TestOrder extends AggregateRoot<String> {

        private String id;
        private String status;

        public TestOrder() {
        }

        TestOrder(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        String status() {
            return status;
        }

        void create() {
            apply(new OrderCreated(new TestOrderId(id)));
        }

        void rename(String name) {
            apply(new OrderRenamed(new TestOrderId(id), name));
        }

        void archive() {
            apply(new OrderArchived(new TestOrderId(id)));
        }

        private void onOrderCreated(OrderCreated event) {
            // 重放重建：聚合标识由首个事件恢复（无参构造后 id 为空）
            this.id = event.getEntityId().asString();
            this.status = "created";
        }

        private void onOrderRenamed(OrderRenamed event) {
            this.status = event.getName();
        }

        private void onOrderArchived(OrderArchived event) {
            this.status = "archived";
        }
    }

    /** 事件样例：订单已创建。 */
    public static final class OrderCreated extends DomainEvent<TestOrderId> {

        public OrderCreated() {
            super();
        }

        OrderCreated(TestOrderId id) {
            super(new EntityIdPath(id));
        }
    }

    /** 事件样例：订单已重命名。 */
    public static final class OrderRenamed extends DomainEvent<TestOrderId> {

        private String name;

        public OrderRenamed() {
            super();
        }

        OrderRenamed(TestOrderId id, String name) {
            super(new EntityIdPath(id));
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** 事件样例：订单已归档。 */
    public static final class OrderArchived extends DomainEvent<TestOrderId> {

        public OrderArchived() {
            super();
        }

        OrderArchived(TestOrderId id) {
            super(new EntityIdPath(id));
        }
    }
}
