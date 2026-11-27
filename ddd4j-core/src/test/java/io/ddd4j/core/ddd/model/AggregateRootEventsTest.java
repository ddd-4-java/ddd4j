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

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extended tests for {@link AggregateRoot} event management and rich methods.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class AggregateRootEventsTest {

    /**
     * 测试执行前执行 <code>BaseContext.clear()</code>、<code>ThreadContext.clear()</code>、<code>RepositoryRegistry.unregister()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code>、<code>ThreadContext.clear()</code>、<code>RepositoryRegistry.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    /**
     * 验证<code>order.hasDomainEvents()</code>为真，<code>order.domainEvents()</code> 的大小为 <code>1</code>，<code>((TestEvent) order.domainEvents().get(0)).getName()</code> 等于 <code>"created"</code>。
     */
    @Test
    void registerEvent_shouldExposeEventViaDomainEvents() {
        Order order = new Order("o1");
        order.markCreated();

        assertThat(order.hasDomainEvents()).isTrue();
        assertThat(order.domainEvents()).hasSize(1);
        assertThat(order.domainEvents().get(0)).isInstanceOf(TestEvent.class);
        assertThat(((TestEvent) order.domainEvents().get(0)).getName()).isEqualTo("created");
    }

    /**
     * 验证<code>events.add(new TestEvent("x"))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void domainEvents_shouldBeImmutableView() {
        Order order = new Order("o1");
        order.markCreated();

        List<DomainEvent<?>> events = order.domainEvents();

        assertThatThrownBy(() -> events.add(new TestEvent("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证<code>pulled</code> 的大小为 <code>1</code>，<code>order.hasDomainEvents()</code>为假。
     */
    @Test
    void pullDomainEvents_shouldReturnAndClearBuffer() {
        Order order = new Order("o1");
        order.markCreated();

        List<DomainEvent<?>> pulled = order.pullDomainEvents();

        assertThat(pulled).hasSize(1);
        assertThat(order.hasDomainEvents()).isFalse();
    }

    /**
     * 验证<code>pulled</code> 的大小为 <code>2</code>，<code>((TestEvent) pulled.get(0)).getName()</code> 等于 <code>"created"</code>，<code>((TestEvent) order.domainEvents().get(0)).getName()</code> 等于 <code>"shipped"</code>。
     */
    @Test
    void pullDomainEvents_shouldKeepAnImmutableSnapshotInEventOrder() {
        Order order = new Order("o1");
        order.markCreated();
        order.rename("paid");

        List<DomainEvent<?>> pulled = order.pullDomainEvents();
        order.rename("shipped");

        assertThat(pulled).hasSize(2);
        assertThat(((TestEvent) pulled.get(0)).getName()).isEqualTo("created");
        assertThat(((TestEvent) pulled.get(1)).getName()).isEqualTo("paid");
        assertThatThrownBy(() -> pulled.add(new TestEvent("late")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(order.domainEvents()).hasSize(1);
        assertThat(((TestEvent) order.domainEvents().get(0)).getName()).isEqualTo("shipped");
    }

    /**
     * 验证<code>order.hasDomainEvents()</code>为假，<code>order.domainEvents()</code>为空。
     */
    @Test
    void clearDomainEvents_shouldEmptyBuffer() {
        Order order = new Order("o1");
        order.markCreated();

        order.clearDomainEvents();

        assertThat(order.hasDomainEvents()).isFalse();
        assertThat(order.domainEvents()).isEmpty();
    }

    /**
     * 验证<code>order.raiseNullEvent()</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void registerEvent_shouldThrowNpeForNullEvent() {
        Order order = new Order("o1");

        assertThatThrownBy(() -> order.raiseNullEvent())
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * 验证<code>order.hasDomainEvents()</code>为假。
     */
    @Test
    void hasDomainEvents_shouldBeFalseInitially() {
        Order order = new Order("o1");

        assertThat(order.hasDomainEvents()).isFalse();
    }

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>((TestEvent) events.get(0)).getName()</code> 等于 <code>"created"</code>，<code>((TestEvent) events.get(1)).getName()</code> 等于 <code>"paid"</code>。
     */
    @Test
    void registerEvent_shouldBufferMultipleEventsInOrder() {
        Order order = new Order("o1");
        order.markCreated();
        order.rename("paid");

        List<DomainEvent<?>> events = order.domainEvents();

        assertThat(events).hasSize(2);
        assertThat(((TestEvent) events.get(0)).getName()).isEqualTo("created");
        assertThat(((TestEvent) events.get(1)).getName()).isEqualTo("paid");
    }

    /**
     * 验证<code>repo.saved</code> 指向同一实例 <code>order</code>，<code>saved</code> 指向同一实例 <code>order</code>。
     */
    @Test
    void save_shouldDelegateToRegisteredRepository() {
        RecordingRepository repo = new RecordingRepository();
        RepositoryRegistry.register(Order.class, repo);
        Order order = new Order("o1");

        Order saved = order.save();

        assertThat(repo.saved).isSameAs(order);
        assertThat(saved).isSameAs(order);
    }

    /**
     * 验证<code>repo.deleted</code> 指向同一实例 <code>order</code>。
     */
    @Test
    void delete_shouldDelegateToRegisteredRepository() {
        RecordingRepository repo = new RecordingRepository();
        RepositoryRegistry.register(Order.class, repo);
        Order order = new Order("o1");

        order.delete();

        assertThat(repo.deleted).isSameAs(order);
    }

    // ========================= Fixtures =========================

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    static final class Order extends AggregateRoot<String> {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id;

        /**
         * 设置 <code>id</code>，用于构造 <code>Order</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        Order(String id) {
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
         */
        void markCreated() {
            registerEvent(new TestEvent("created"));
        }

        /**
         * 调用 <code>registerEvent()</code> 执行当前夹具操作。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        void rename(String name) {
            registerEvent(new TestEvent(name));
        }

        /**
         * 调用 <code>registerEvent()</code> 执行当前夹具操作。
         */
        void raiseNullEvent() {
            registerEvent(null);
        }
    }

    /**
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    static final class TestEvent extends DomainEvent<OrderId> {

        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;

        /**
         * 设置 <code>name</code>，用于构造 <code>TestEvent</code> 测试实例。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        TestEvent(String name) {
            super(new EntityIdPath(new OrderId(name)));
            this.name = name;
        }

        /**
         * 读取<code>name</code> 字段。
         *
         * @return <code>name</code> 字段
         */
        String getName() {
            return name;
        }

    }

    /**
     * 订单测试聚合的标识类型。
     */
    static final class OrderId implements AggregateRootId {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new OrderEntityType();

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
     * 测试订单聚合的实体类型标识。
     */
    static final class OrderEntityType implements EntityType {

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>"Order"</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return "Order";
        }
    }

    /**
     * 记录仓储操作，供测试核对命令或投影写入。
     */
    static class RecordingRepository implements Repository<Order, Serializable> {
        /**
         * 用来断言仓储实际保存内容或保存状态的记录。
         */
        Order saved;
        /**
         * 记录执行删除后观察到的状态或数据。
         */
        Order deleted;

        /**
         * 通过 <code>Optional.of()</code> 取得当前测试需要的结果。
         *
         * @param id 当前要查找的测试记录标识
         * @return 当前测试使用的结果，取自 <code>Optional.of(new Order(String.valueOf(id)))</code>
         */
        @Override
        public Optional<Order> findById(Serializable id) {
            return Optional.of(new Order(String.valueOf(id)));
        }

        /**
         * 写入或更新给定的领域对象或存储记录。
         *
         * <p>此实现更新<code>this.saved = aggregate</code>。</p>
         *
         * @param aggregate 待处理聚合根
         * @return 实际返回<code>aggregate</code>，供调用方取得当前值
         */
        @Override
        public Order save(Order aggregate) {
            this.saved = aggregate;
            return aggregate;
        }

        /**
         * 删除指定记录。
         *
         * <p>此实现更新<code>this.deleted = aggregate</code>。</p>
         *
         * @param aggregate 待处理聚合根
         */
        @Override
        public void delete(Order aggregate) {
            this.deleted = aggregate;
        }
    }
}
