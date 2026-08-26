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
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setUp() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
        RepositoryRegistry.unregister(Order.class);
    }

    @Test
    @DisplayName("apply 应路由到 on<OrderCreated> 方法，更新聚合状态")
    void apply_shouldRouteToOnEventTypeMethod() {
        Order order = new Order("o1");

        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        assertThat(order.getOrderNo()).isEqualTo("ORD-001");
    }

    @Test
    @DisplayName("apply 应路由到 on<OrderPaid> 方法")
    void apply_shouldRouteToOnOrderPaidMethod() {
        Order order = new Order("o1");
        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        order.applyEvent(new OrderPaid("o1", "PAID"));

        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("apply 无对应 handler 时应抛 IllegalStateException（2.0.x 严格语义）")
    void apply_noHandler_shouldThrow() {
        Order order = new Order("o1");

        // OrderShipped 没有定义 onOrderShipped 方法
        assertThatThrownBy(() -> order.applyEvent(new OrderShipped("o1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No @EventHandler method found");
    }

    @Test
    @DisplayName("loadFromHistory 应按顺序重建聚合状态")
    void loadFromHistory_shouldRebuildStateInOrder() {
        Order order = new Order("o1");

        List<DomainEvent<?>> history = List.of(
                new OrderCreated("o1", "ORD-001"),
                new OrderPaid("o1", "PAID")
        );
        order.loadFromHistoryFromList(history);

        assertThat(order.getOrderNo()).isEqualTo("ORD-001");
        assertThat(order.getStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("loadFromHistory 空列表应不改变状态")
    void loadFromHistory_emptyList_shouldNotChangeState() {
        Order order = new Order("o1");

        order.loadFromHistoryFromList(List.of());

        assertThat(order.getOrderNo()).isNull();
    }

    @Test
    @DisplayName("loadFromHistory null 应不改变状态")
    void loadFromHistory_null_shouldNotChangeState() {
        Order order = new Order("o1");

        order.loadFromHistoryFromList(null);

        assertThat(order.getOrderNo()).isNull();
    }

    @Test
    @DisplayName("apply 应将事件注册到未提交事件缓冲区（2.0.x 语义）")
    void apply_shouldRegisterEventToBuffer() {
        Order order = new Order("o1");

        order.applyEvent(new OrderCreated("o1", "ORD-001"));

        assertThat(order.hasDomainEvents()).isTrue();
        assertThat(order.domainEvents()).hasSize(1);
    }

    // ========================= Fixtures =========================

    static final class Order extends AggregateRoot<String> {
        private final String id;
        private String orderNo;
        private String status;

        Order(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        String getOrderNo() {
            return orderNo;
        }

        String getStatus() {
            return status;
        }

        /**
         * 测试入口：暴露 apply 为 package-private 便于测试。
         */
        <E extends DomainEvent<?>> void applyEvent(E event) {
            apply(event);
        }

        /**
         * 测试入口：暴露 loadFromHistory。
         */
        void loadFromHistoryFromList(List<? extends DomainEvent<?>> events) {
            loadFromHistory(events);
        }

        @SuppressWarnings("unused")
        private void onOrderCreated(OrderCreated event) {
            this.orderNo = event.orderNo;
        }

        @SuppressWarnings("unused")
        private void onOrderPaid(OrderPaid event) {
            this.status = event.status;
        }

        // 注意：没有定义 onOrderShipped，用于测试无 handler 的情况
    }

    static final class OrderCreated extends DomainEvent<OrderId> {
        final String orderNo;

        OrderCreated(String entityId, String orderNo) {
            super(new EntityIdPath(new OrderId(entityId)));
            this.orderNo = orderNo;
        }
    }

    static final class OrderPaid extends DomainEvent<OrderId> {
        final String status;

        OrderPaid(String entityId, String status) {
            super(new EntityIdPath(new OrderId(entityId)));
            this.status = status;
        }
    }

    static final class OrderShipped extends DomainEvent<OrderId> {
        OrderShipped(String entityId) {
            super(new EntityIdPath(new OrderId(entityId)));
        }
    }

    static final class OrderId implements AggregateRootId {

        private static final EntityType TYPE = new OrderEntityType();

        private final String value;

        OrderId(String value) {
            this.value = value;
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
            return TYPE.asString() + " " + value;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OrderId)) return false;
            return value.equals(((OrderId) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    static final class OrderEntityType implements EntityType {
        @Override
        public String asString() {
            return "Order";
        }
    }
}
