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
package io.ddd4j.core.ddd.model;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.*;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>AggregateRoot</code> 在<code>shouldPublishBufferedLocalEvents</code>等操作中的行为契约。
 */
class AggregateRootTest {
    /**
     * 验证<code>pending.get(0).publish()</code> 等于 <code>"handled"</code>，<code>received</code> 等于 <code>List.of("renamed")</code>，<code>aggregate.hasDomainEvents()</code>为假。
     */
    @Test
    void shouldPublishBufferedLocalEventsWithoutCommandBusEventStoreOrProjection() {
        List<String> received = new ArrayList<>();
        DomainEventPublisher publisher = new DomainEventPublisher() {
            @Override
            public <ID extends EntityId> void publish(DomainEvent<ID> event) {
                if (event instanceof TestDomainEvent && event.tenantIn("tenant-a")) {
                    received.add(((TestDomainEvent) event).getName());
                    event.setResult("handled");
                }
            }
        };
        ThreadContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);
        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-a");
        try {
            TestAggregate aggregate = new TestAggregate("local-1");
            aggregate.rename("renamed");
            List<DomainEvent<?>> pending = aggregate.pullDomainEvents();
            assertEquals("handled", pending.get(0).publish());
            assertEquals(List.of("renamed"), received);
            assertFalse(aggregate.hasDomainEvents());
            ThreadContext.set(ContextConstants.TENANT_ID, "tenant-b");
            new TestDomainEvent("other-tenant").publish();
            assertEquals(List.of("renamed"), received);
        } finally {
            ThreadContext.clear();
        }
    }

    /**
     * 验证<code>aggregate.pullDomainEvents().size()</code> 等于 <code>1</code>，<code>aggregate.pullDomainEvents().size()</code> 等于 <code>0</code>。
     */
    @Test
    void shouldCollectNewEventsButNotReplayHistory() {
        AppliedAggregate aggregate = new AppliedAggregate();
        TestEvent created = new TestEvent();
        aggregate.record(created);
        assertEquals(1, aggregate.pullDomainEvents().size());
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(0, aggregate.pullDomainEvents().size());
    }

    /**
     * 验证<code>aggregate.handled</code> 等于 <code>1</code>，<code>aggregate.handled</code> 等于 <code>2</code>，被测调用 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void shouldDispatchApplyAndReplayToEventHandler() {
        HandlingAggregate aggregate = new HandlingAggregate();
        aggregate.record(new TestEvent());
        assertEquals(1, aggregate.handled);
        aggregate.loadFromHistory(Collections.<DomainEvent<?>>singletonList(new TestEvent()));
        assertEquals(2, aggregate.handled);
        assertThrows(IllegalStateException.class, () -> new AggregateRoot<StringEntityId>() {
            @Override
            public StringEntityId id() {
                return new StringEntityId("missing");
            }
        }.apply(new TestEvent()));
    }

    /**
     * 验证<code>second.id()</code> 等于 <code>first.id()</code>，<code>first.sameIdentityAs(second)</code> 等于 <code>true</code>。
     */
    @Test
    void shouldExposeDomainIdentityAndCompareEntitiesById() {
        IdentityAggregate first = new IdentityAggregate(new StringEntityId("same"));
        IdentityAggregate second = new IdentityAggregate(new StringEntityId("same"));
        assertEquals(first.id(), second.id());
        assertEquals(true, first.sameIdentityAs(second));
    }

    /**
     * 验证<code>aggregate.save()</code> 等于 <code>aggregate</code>，<code>repository.saves</code> 等于 <code>2</code>，<code>repository.deletes</code> 等于 <code>1</code>。
     */
    @Test
    void shouldPersistThroughRegisteredRepository() {
        PersistentAggregate aggregate = new PersistentAggregate(new StringEntityId("persisted"));
        RecordingRepository repository = new RecordingRepository();
        RepositoryRegistry.register(PersistentAggregate.class, repository);
        assertEquals(aggregate, aggregate.save());
        aggregate.update();
        aggregate.delete();
        assertEquals(2, repository.saves);
        assertEquals(1, repository.deletes);
        RepositoryRegistry.unregister(PersistentAggregate.class);
    }

    /**
     * 验证<code>aggregate.hasDomainEvents()</code>为真，<code>aggregate.domainEvents().size()</code> 等于 <code>1</code>，<code>((TestDomainEvent) events.get(0)).getName()</code> 等于 <code>"new-name"</code>。
     */
    @Test
    void shouldBufferPullAndClearDomainEvents() {
        TestAggregate aggregate = new TestAggregate("order-1");

        aggregate.rename("new-name");

        assertTrue(aggregate.hasDomainEvents());
        assertEquals(1, aggregate.domainEvents().size());

        List<DomainEvent<?>> events = aggregate.pullDomainEvents();

        assertEquals(1, events.size());
        assertFalse(aggregate.hasDomainEvents());
        assertTrue(events.get(0) instanceof TestDomainEvent);
        assertEquals("new-name", ((TestDomainEvent) events.get(0)).getName());
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void shouldRejectNullDomainEvent() {
        TestAggregate aggregate = new TestAggregate("order-1");

        assertThrows(NullPointerException.class, aggregate::raiseNullEvent);
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    private static final class AppliedAggregate extends AggregateRoot<StringEntityId> {
        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        void record(TestEvent event) {
            apply(event);
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @EventHandler
        private void on(TestEvent event) {
        }

        /**
         * 创建 <code>new StringEntityId</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new StringEntityId("order-1")</code>
         */
        @Override
        public StringEntityId id() {
            return new StringEntityId("order-1");
        }
    }

    /**
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    private static final class TestEvent extends DomainEvent<TestRootId> {
        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         */
        TestEvent() {
            super(new EntityIdPath(new TestRootId("order-1")));
        }
    }

    /**
     * 为测试聚合提供独立标识的值对象。
     */
    private static final class TestRootId implements AggregateRootId {
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");
        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestRootId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        private TestRootId(String value) {
            this.value = value;
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
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    private static final class HandlingAggregate extends AggregateRoot<StringEntityId> {
        /**
         * 当前测试中已经交给处理器的记录。
         */
        private int handled;

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        void record(TestEvent event) {
            apply(event);
        }

        /**
         * 记录聚合已处理一条测试事件。
         *
         * @param event 由聚合或监听器收到的测试事件
         */
        @EventHandler
        private void on(TestEvent event) {
            handled++;
        }

        /**
         * 创建 <code>new StringEntityId</code> 供当前测试路径调用。
         *
         * @return 当前测试使用的结果，取自 <code>new StringEntityId("order-1")</code>
         */
        @Override
        public StringEntityId id() {
            return new StringEntityId("order-1");
        }
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    private static final class IdentityAggregate extends AggregateRoot<StringEntityId> {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final StringEntityId id;

        /**
         * 设置 <code>id</code>，用于构造 <code>IdentityAggregate</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        private IdentityAggregate(StringEntityId id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public StringEntityId id() {
            return id;
        }
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    private static final class PersistentAggregate extends AggregateRoot<StringEntityId> {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final StringEntityId id;

        /**
         * 设置 <code>id</code>，用于构造 <code>PersistentAggregate</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        private PersistentAggregate(StringEntityId id) {
            this.id = id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public StringEntityId id() {
            return id;
        }
    }

    /**
     * 记录仓储操作，供测试核对命令或投影写入。
     */
    private static final class RecordingRepository implements Repository<PersistentAggregate, StringEntityId> {
        /**
         * 用于观察仓储写入调用次数或记录的计数。
         */
        private int saves;
        /**
         * 测试替身收到的删除操作次数。
         */
        private int deletes;

        /**
         * 通过 <code>Optional.empty()</code> 取得当前测试需要的结果。
         *
         * @param id 当前要查找的测试记录标识
         * @return 当前测试使用的结果，取自 <code>Optional.empty()</code>
         */
        @Override
        public Optional<PersistentAggregate> findById(StringEntityId id) {
            return Optional.empty();
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * @param aggregate 待处理聚合根
         * @return 实际返回<code>aggregate</code>，供调用方取得当前值
         */
        @Override
        public PersistentAggregate save(PersistentAggregate aggregate) {
            saves++;
            return aggregate;
        }

        /**
         * 记录对测试实体执行的一次删除调用。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         */
        @Override
        public void deleteById(StringEntityId id) {
            deletes++;
        }
    }

    /**
     * 测试专用聚合，供验证聚合标识和仓储或事件处理行为。
     */
    private static final class TestAggregate extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id;

        /**
         * 设置 <code>id</code>，用于构造 <code>TestAggregate</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        private TestAggregate(String id) {
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
         * 调用 <code>registerEvent()</code> 执行当前夹具操作。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        private void rename(String name) {
            registerEvent(new TestDomainEvent(name));
        }

        /**
         * 调用 <code>registerEvent()</code> 执行当前夹具操作。
         */
        private void raiseNullEvent() {
            registerEvent(null);
        }
    }

    /**
     * 供传输、序列化或处理器分发验证的领域事件样本。
     */
    private static final class TestDomainEvent extends DomainEvent<OrderId> {

        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;

        /**
         * 设置 <code>name</code>，用于构造 <code>TestDomainEvent</code> 测试实例。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        private TestDomainEvent(String name) {
            super(new EntityIdPath(new OrderId(name)));
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

    }

    /**
     * 订单测试聚合的标识类型。
     */
    private static final class OrderId implements AggregateRootId {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new TestEntityType("Order");

        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>OrderId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        OrderId(String value) {
            this.value = value;
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
         * @return 实际返回<code>TYPE.asString() + " " + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + " " + value;
        }

        /**
         * 生成用于调试和日志的对象描述。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return value;
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * @param o 用来比较的另一个对象，允许为空
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof OrderId)) {
                return false;
            }
            return value.equals(((OrderId) o).value);
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
    }

    /**
     * 实现 <code>EntityType</code>，供测试调用 <code>asString()</code> 并观察结果。
     */
    private static final class TestEntityType implements EntityType {

        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;

        /**
         * 设置 <code>name</code>，用于构造 <code>TestEntityType</code> 测试实例。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        TestEntityType(String name) {
            this.name = name;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>name</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return name;
        }
    }
}
