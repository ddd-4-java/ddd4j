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

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>BaseContext.clear()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>RepositoryRegistry.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>RepositoryRegistry.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    /** \u9a8c\u8bc1<code>order.hasDomainEvents()</code>\u4e3a\u771f\uff0c<code>order.domainEvents()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>((TestEvent) order.domainEvents().get(0)).getName()</code> \u7b49\u4e8e <code>"created"</code>\u3002 */
    @Test
    void registerEvent_shouldExposeEventViaDomainEvents() {
        Order order = new Order("o1");
        order.markCreated();

        assertThat(order.hasDomainEvents()).isTrue();
        assertThat(order.domainEvents()).hasSize(1);
        assertThat(order.domainEvents().get(0)).isInstanceOf(TestEvent.class);
        assertThat(((TestEvent) order.domainEvents().get(0)).getName()).isEqualTo("created");
    }

    /** \u9a8c\u8bc1<code>events.add(new TestEvent("x"))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void domainEvents_shouldBeImmutableView() {
        Order order = new Order("o1");
        order.markCreated();

        List<DomainEvent<?>> events = order.domainEvents();

        assertThatThrownBy(() -> events.add(new TestEvent("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** \u9a8c\u8bc1<code>pulled</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>order.hasDomainEvents()</code>\u4e3a\u5047\u3002 */
    @Test
    void pullDomainEvents_shouldReturnAndClearBuffer() {
        Order order = new Order("o1");
        order.markCreated();

        List<DomainEvent<?>> pulled = order.pullDomainEvents();

        assertThat(pulled).hasSize(1);
        assertThat(order.hasDomainEvents()).isFalse();
    }

    /** \u9a8c\u8bc1<code>pulled</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>((TestEvent) pulled.get(0)).getName()</code> \u7b49\u4e8e <code>"created"</code>\uff0c<code>((TestEvent) order.domainEvents().get(0)).getName()</code> \u7b49\u4e8e <code>"shipped"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>order.hasDomainEvents()</code>\u4e3a\u5047\uff0c<code>order.domainEvents()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void clearDomainEvents_shouldEmptyBuffer() {
        Order order = new Order("o1");
        order.markCreated();

        order.clearDomainEvents();

        assertThat(order.hasDomainEvents()).isFalse();
        assertThat(order.domainEvents()).isEmpty();
    }

    /** \u9a8c\u8bc1<code>order.raiseNullEvent()</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void registerEvent_shouldThrowNpeForNullEvent() {
        Order order = new Order("o1");

        assertThatThrownBy(() -> order.raiseNullEvent())
                .isInstanceOf(NullPointerException.class);
    }

    /** \u9a8c\u8bc1<code>order.hasDomainEvents()</code>\u4e3a\u5047\u3002 */
    @Test
    void hasDomainEvents_shouldBeFalseInitially() {
        Order order = new Order("o1");

        assertThat(order.hasDomainEvents()).isFalse();
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>((TestEvent) events.get(0)).getName()</code> \u7b49\u4e8e <code>"created"</code>\uff0c<code>((TestEvent) events.get(1)).getName()</code> \u7b49\u4e8e <code>"paid"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>repo.saved</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>order</code>\uff0c<code>saved</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>order</code>\u3002 */
    @Test
    void save_shouldDelegateToRegisteredRepository() {
        RecordingRepository repo = new RecordingRepository();
        RepositoryRegistry.register(Order.class, repo);
        Order order = new Order("o1");

        Order saved = order.save();

        assertThat(repo.saved).isSameAs(order);
        assertThat(saved).isSameAs(order);
    }

    /** \u9a8c\u8bc1<code>repo.deleted</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>order</code>\u3002 */
    @Test
    void delete_shouldDelegateToRegisteredRepository() {
        RecordingRepository repo = new RecordingRepository();
        RepositoryRegistry.register(Order.class, repo);
        Order order = new Order("o1");

        order.delete();

        assertThat(repo.deleted).isSameAs(order);
    }

    // ========================= Fixtures =========================

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id;

        /**
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>Order</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        Order(String id) {
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

        /** \u8c03\u7528 <code>registerEvent()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void markCreated() {
            registerEvent(new TestEvent("created"));
        }

        /**
         * \u8c03\u7528 <code>registerEvent()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        void rename(String name) {
            registerEvent(new TestEvent(name));
        }

        /** \u8c03\u7528 <code>registerEvent()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void raiseNullEvent() {
            registerEvent(null);
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    static final class TestEvent extends DomainEvent<OrderId> {

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;

        /**
         * \u8bbe\u7f6e <code>name</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TestEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        TestEvent(String name) {
            super(new EntityIdPath(new OrderId(name)));
            this.name = name;
        }

        /**
         * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
         * @return <code>name</code> \u5b57\u6bb5
         */
        String getName() {
            return name;
        }

    }

    /** \u8ba2\u5355\u6d4b\u8bd5\u805a\u5408\u7684\u6807\u8bc6\u7c7b\u578b\u3002 */
    static final class OrderId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new OrderEntityType();

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>OrderId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
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

    /** \u6d4b\u8bd5\u8ba2\u5355\u805a\u5408\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
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

    /** \u8bb0\u5f55\u4ed3\u50a8\u64cd\u4f5c\uff0c\u4f9b\u6d4b\u8bd5\u6838\u5bf9\u547d\u4ee4\u6216\u6295\u5f71\u5199\u5165\u3002 */
    static class RecordingRepository implements Repository<Order, Serializable> {
        /** \u7528\u6765\u65ad\u8a00\u4ed3\u50a8\u5b9e\u9645\u4fdd\u5b58\u5185\u5bb9\u6216\u4fdd\u5b58\u72b6\u6001\u7684\u8bb0\u5f55\u3002 */
        Order saved;
        /** \u8bb0\u5f55\u6267\u884c\u5220\u9664\u540e\u89c2\u5bdf\u5230\u7684\u72b6\u6001\u6216\u6570\u636e\u3002 */
        Order deleted;

        /**
         * \u901a\u8fc7 <code>Optional.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Optional.of(new Order(String.valueOf(id)))</code>
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
