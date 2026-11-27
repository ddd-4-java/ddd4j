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
     * 验证 {@code apply} 将 {@code OrderCreated} 交给对应处理器并更新订单号。
     */
    @Test
    @DisplayName("apply 应路由到 on<OrderCreated> 方法，更新聚合状态")
    void apply_shouldRouteToOnEventTypeMethod() {
        Order order = new Order("o1");

        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        assertThat(order.getOrderNo()).isEqualTo("ORD-001");
    }

    /**
     * 验证 {@code apply} 将 {@code OrderPaid} 交给对应处理器。
     */
    @Test
    @DisplayName("apply 应路由到 on<OrderPaid> 方法")
    void apply_shouldRouteToOnOrderPaidMethod() {
        Order order = new Order("o1");
        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        order.applyEvent(new OrderPaid("o1", "PAID"));

        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    /**
     * apply 无对应 handler 时应抛 IllegalStateException（2.0.x 严格语义）。
     */
    @Test
    @DisplayName("apply 无对应 handler 时应抛 IllegalStateException（2.0.x 严格语义）")
    void apply_noHandler_shouldThrow() {
        Order order = new Order("o1");

        // OrderShipped 没有定义 onOrderShipped 方法
        assertThatThrownBy(() -> order.applyEvent(new OrderShipped("o1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No @EventHandler method found");
    }

    /**
     * loadFromHistory 应按顺序重建聚合状态。
     */
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

    /**
     * loadFromHistory 空列表应不改变状态。
     */
    @Test
    @DisplayName("loadFromHistory 空列表应不改变状态")
    void loadFromHistory_emptyList_shouldNotChangeState() {
        Order order = new Order("o1");

        order.loadFromHistoryFromList(Collections.emptyList());

        assertThat(order.getOrderNo()).isNull();
    }

    /**
     * loadFromHistory null 应不改变状态。
     */
    @Test
    @DisplayName("loadFromHistory null 应不改变状态")
    void loadFromHistory_null_shouldNotChangeState() {
        Order order = new Order("o1");

        order.loadFromHistoryFromList(null);

        assertThat(order.getOrderNo()).isNull();
    }

    /**
     * apply 应将事件注册到未提交事件缓冲区（2.0.x 语义）。
     */
    @Test
    @DisplayName("apply 应将事件注册到未提交事件缓冲区（2.0.x 语义）")
    void apply_shouldRegisterEventToBuffer() {
        Order order = new Order("o1");

        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        assertThat(order.hasDomainEvents()).isTrue();
        assertThat(order.domainEvents()).hasSize(1);
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
         * 租户内使用的业务订单号。
         */
        private String orderNo;
        /**
         * 测试替身返回、模拟或捕获的状态。
         */
        private String status;

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
         * 读取租户内使用的业务订单号。
         *
         * @return 租户内使用的业务订单号
         */
        String getOrderNo() {
            return orderNo;
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        String getStatus() {
            return status;
        }

        /**
         * 测试入口：暴露 apply 为 package-private 便于测试。
         *
         * @param <E>   被应用的领域事件类型
         * @param event 要应用到订单聚合的领域事件
         */
        <E extends DomainEvent<?>> void applyEvent(E event) {
            apply(event);
        }

        /**
         * 测试入口：暴露 loadFromHistory。
         *
         * @param events 按顺序追加或回放的领域事件
         */
        void loadFromHistoryFromList(List<? extends DomainEvent<?>> events) {
            loadFromHistory(events);
        }

        /**
         * 根据本次调用更新 <code>this.orderNo = event.orderNo</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @SuppressWarnings("unused")
        private void onOrderCreated(OrderCreated event) {
            this.orderNo = event.orderNo;
        }

        /**
         * 根据本次调用更新 <code>this.status = event.status</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @SuppressWarnings("unused")
        private void onOrderPaid(OrderPaid event) {
            this.status = event.status;
        }

        // 注意：没有定义 onOrderShipped，用于测试无 handler 的情况
    }

    /**
     * 表示订单创建的测试事件。
     */
    static final class OrderCreated extends DomainEvent<OrderId> {
        /**
         * 租户内使用的业务订单号。
         */
        final String orderNo;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreated</code> 测试夹具。
         *
         * @param entityId 用于初始化 <code>OrderCreated</code> 的 <code>String</code> 值
         * @param orderNo  租户内使用的业务订单号
         */
        OrderCreated(String entityId, String orderNo) {
            super(new EntityIdPath(new OrderId(entityId)));
            this.orderNo = orderNo;
        }
    }

    /**
     * 表示订单完成付款的测试事件。
     */
    static final class OrderPaid extends DomainEvent<OrderId> {
        /**
         * 测试替身返回、模拟或捕获的状态。
         */
        final String status;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderPaid</code> 测试夹具。
         *
         * @param entityId 用于初始化 <code>OrderPaid</code> 的 <code>String</code> 值
         * @param status   测试替身返回、模拟或捕获的状态
         */
        OrderPaid(String entityId, String status) {
            super(new EntityIdPath(new OrderId(entityId)));
            this.status = status;
        }
    }

    /**
     * 表示订单已经发运的测试事件。
     */
    static final class OrderShipped extends DomainEvent<OrderId> {
        /**
         * 使用 <code>super()</code> 初始化 <code>OrderShipped</code> 测试夹具。
         *
         * @param entityId 用于初始化 <code>OrderShipped</code> 的 <code>String</code> 值
         */
        OrderShipped(String entityId) {
            super(new EntityIdPath(new OrderId(entityId)));
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
}
