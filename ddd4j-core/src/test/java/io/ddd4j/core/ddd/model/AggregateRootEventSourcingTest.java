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
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AggregateRoot} 事件溯源测试（apply + loadFromHistory）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@DisplayName("AggregateRoot 事件溯源")
class AggregateRootEventSourcingTest {

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

    /** \u9a8c\u8bc1 {@code apply} \u5c06 {@code OrderCreated} \u4ea4\u7ed9\u5bf9\u5e94\u5904\u7406\u5668\u5e76\u66f4\u65b0\u8ba2\u5355\u53f7\u3002 */
    @Test
    @DisplayName("apply 应路由到 on<OrderCreated> 方法，更新聚合状态")
    void apply_shouldRouteToOnEventTypeMethod() {
        Order order = new Order("o1");

        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        assertThat(order.getOrderNo()).isEqualTo("ORD-001");
    }

    /** \u9a8c\u8bc1 {@code apply} \u5c06 {@code OrderPaid} \u4ea4\u7ed9\u5bf9\u5e94\u5904\u7406\u5668\u3002 */
    @Test
    @DisplayName("apply 应路由到 on<OrderPaid> 方法")
    void apply_shouldRouteToOnOrderPaidMethod() {
        Order order = new Order("o1");
        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        order.applyEvent(new OrderPaid("o1", "PAID"));

        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    /** apply \u65e0\u5bf9\u5e94 handler \u65f6\u5e94\u629b IllegalStateException\uff082.0.x \u4e25\u683c\u8bed\u4e49\uff09\u3002 */
    @Test
    @DisplayName("apply 无对应 handler 时应抛 IllegalStateException（2.0.x 严格语义）")
    void apply_noHandler_shouldThrow() {
        Order order = new Order("o1");

        // OrderShipped 没有定义 onOrderShipped 方法
        assertThatThrownBy(() -> order.applyEvent(new OrderShipped("o1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No @EventHandler method found");
    }

    /** loadFromHistory \u5e94\u6309\u987a\u5e8f\u91cd\u5efa\u805a\u5408\u72b6\u6001\u3002 */
    @Test
    @DisplayName("loadFromHistory 应按顺序重建聚合状态")
    void loadFromHistory_shouldRebuildStateInOrder() {
        Order order = new Order("o1");

        List<DomainEvent<?>> history = Arrays.asList(
                new OrderCreated("o1", "ORD-001"),
                new OrderPaid("o1", "PAID")
        );
        order.loadFromHistoryFromList(history);

        assertThat(order.getOrderNo()).isEqualTo("ORD-001");
        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    /** loadFromHistory \u7a7a\u5217\u8868\u5e94\u4e0d\u6539\u53d8\u72b6\u6001\u3002 */
    @Test
    @DisplayName("loadFromHistory 空列表应不改变状态")
    void loadFromHistory_emptyList_shouldNotChangeState() {
        Order order = new Order("o1");

        order.loadFromHistoryFromList(Collections.emptyList());

        assertThat(order.getOrderNo()).isNull();
    }

    /** loadFromHistory null \u5e94\u4e0d\u6539\u53d8\u72b6\u6001\u3002 */
    @Test
    @DisplayName("loadFromHistory null 应不改变状态")
    void loadFromHistory_null_shouldNotChangeState() {
        Order order = new Order("o1");

        order.loadFromHistoryFromList(null);

        assertThat(order.getOrderNo()).isNull();
    }

    /** apply \u5e94\u5c06\u4e8b\u4ef6\u6ce8\u518c\u5230\u672a\u63d0\u4ea4\u4e8b\u4ef6\u7f13\u51b2\u533a\uff082.0.x \u8bed\u4e49\uff09\u3002 */
    @Test
    @DisplayName("apply 应将事件注册到未提交事件缓冲区（2.0.x 语义）")
    void apply_shouldRegisterEventToBuffer() {
        Order order = new Order("o1");

        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        assertThat(order.hasDomainEvents()).isTrue();
        assertThat(order.domainEvents()).hasSize(1);
    }

    // ========================= Fixtures =========================

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id;
        /** \u79df\u6237\u5185\u4f7f\u7528\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7\u3002 */
        private String orderNo;
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        private String status;

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

        /**
         * \u8bfb\u53d6\u79df\u6237\u5185\u4f7f\u7528\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7\u3002
         * @return \u79df\u6237\u5185\u4f7f\u7528\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7
         */
        String getOrderNo() {
            return orderNo;
        }

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        String getStatus() {
            return status;
        }

        /**
         * \u6d4b\u8bd5\u5165\u53e3\uff1a\u66b4\u9732 apply \u4e3a package-private \u4fbf\u4e8e\u6d4b\u8bd5\u3002
         *
         * @param <E> \u88ab\u5e94\u7528\u7684\u9886\u57df\u4e8b\u4ef6\u7c7b\u578b
         * @param event \u8981\u5e94\u7528\u5230\u8ba2\u5355\u805a\u5408\u7684\u9886\u57df\u4e8b\u4ef6
         */
        <E extends DomainEvent<?>> void applyEvent(E event) {
            apply(event);
        }

        /**
         * \u6d4b\u8bd5\u5165\u53e3\uff1a\u66b4\u9732 loadFromHistory\u3002
         *
         * @param events \u6309\u987a\u5e8f\u8ffd\u52a0\u6216\u56de\u653e\u7684\u9886\u57df\u4e8b\u4ef6
         */
        void loadFromHistoryFromList(List<? extends DomainEvent<?>> events) {
            loadFromHistory(events);
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.orderNo = event.orderNo</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @SuppressWarnings("unused")
        private void onOrderCreated(OrderCreated event) {
            this.orderNo = event.orderNo;
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.status = event.status</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @SuppressWarnings("unused")
        private void onOrderPaid(OrderPaid event) {
            this.status = event.status;
        }

        // 注意：没有定义 onOrderShipped，用于测试无 handler 的情况
    }

    /** \u8868\u793a\u8ba2\u5355\u521b\u5efa\u7684\u6d4b\u8bd5\u4e8b\u4ef6\u3002 */
    static final class OrderCreated extends DomainEvent<OrderId> {
        /** \u79df\u6237\u5185\u4f7f\u7528\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7\u3002 */
        final String orderNo;

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreated</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderCreated</code> \u7684 <code>String</code> \u503c
         * @param orderNo \u79df\u6237\u5185\u4f7f\u7528\u7684\u4e1a\u52a1\u8ba2\u5355\u53f7
         */
        OrderCreated(String entityId, String orderNo) {
            super(new EntityIdPath(new OrderId(entityId)));
            this.orderNo = orderNo;
        }
    }

    /** \u8868\u793a\u8ba2\u5355\u5b8c\u6210\u4ed8\u6b3e\u7684\u6d4b\u8bd5\u4e8b\u4ef6\u3002 */
    static final class OrderPaid extends DomainEvent<OrderId> {
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        final String status;

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderPaid</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderPaid</code> \u7684 <code>String</code> \u503c
         * @param status \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001
         */
        OrderPaid(String entityId, String status) {
            super(new EntityIdPath(new OrderId(entityId)));
            this.status = status;
        }
    }

    /** \u8868\u793a\u8ba2\u5355\u5df2\u7ecf\u53d1\u8fd0\u7684\u6d4b\u8bd5\u4e8b\u4ef6\u3002 */
    static final class OrderShipped extends DomainEvent<OrderId> {
        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderShipped</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>OrderShipped</code> \u7684 <code>String</code> \u503c
         */
        OrderShipped(String entityId) {
            super(new EntityIdPath(new OrderId(entityId)));
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
            if (this == o) return true;
            if (!(o instanceof OrderId)) return false;
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
}
