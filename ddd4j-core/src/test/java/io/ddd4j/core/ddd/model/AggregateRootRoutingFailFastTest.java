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

import java.util.Arrays;
import java.util.Objects;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AggregateRoot#apply(DomainEvent)} 子实体路由首段断言（fail-fast）测试。
 *
 * <p>对应参考文档 02-entity-id-path「落地计划」Task 2.2/2.3：
 * 路由取 {@link EntityIdPath#first()} 后必须先断言首段为 {@link AggregateRootId}
 * 再转型（对应 fuin AbstractAggregateRoot :146-149 的 fail-fast），
 * 首段为普通 {@link EntityId} / {@link StringEntityId} 时立即抛
 * {@link IllegalStateException}，错误路径早失败；首段合法时单段直达根处理器、
 * 多段（子实体路径）同样统一在根处理器派发。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class AggregateRootRoutingFailFastTest {

    /** \u9a8c\u8bc1<code>order.apply(new PathEvent(new EntityIdPath(new PlainId("order-1"))))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exception.getMessage().contains("AggregateRootId")</code>\u4e3a\u771f\u3002 */
    @Test
    void applyRejectsPlainEntityIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new PathEvent(new EntityIdPath(new PlainId("order-1")))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "fail-fast message should name the expected root segment type");
    }

    /** \u9a8c\u8bc1<code>order.apply(new PathEvent(new EntityIdPath(new StringEntityId("order-1"))))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exception.getMessage().contains("AggregateRootId")</code>\u4e3a\u771f\u3002 */
    @Test
    void applyRejectsStringEntityIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new PathEvent(new EntityIdPath(new StringEntityId("order-1")))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "StringEntityId convenience root segment must also be rejected");
    }

    /** \u9a8c\u8bc1<code>order.loadFromHistory(Arrays.asList(new PathEvent(new EntityIdPath(new PlainId("order-1")))))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exception.getMessage().contains("AggregateRootId")</code>\u4e3a\u771f\u3002 */
    @Test
    void loadFromHistoryRejectsNonAggregateRootIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.loadFromHistory(Arrays.asList(new PathEvent(new EntityIdPath(new PlainId("order-1"))))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "replay path must fail fast on the same assertion");
    }

    /** \u9a8c\u8bc1<code>order.handled</code> \u7b49\u4e8e <code>1</code>\uff0c<code>order.domainEvents().contains(event)</code>\u4e3a\u771f\u3002 */
    @Test
    void applyAcceptsAggregateRootIdRootSegment() {
        Order order = new Order("order-1");
        PathEvent event = new PathEvent(new EntityIdPath(new RootId("order-1")));

        order.apply(event);

        assertEquals(1, order.handled, "single-segment path should dispatch to root handler");
        assertTrue(order.domainEvents().contains(event), "applied event should be registered");
    }

    /** \u9a8c\u8bc1<code>order.handled</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void applyAcceptsSubEntityPathRootedAtAggregateRootId() {
        Order order = new Order("order-1");
        EntityIdPath subEntityPath = new EntityIdPath(Arrays.<EntityId>asList(new RootId("order-1"), new PlainId("item-1")));

        order.apply(new PathEvent(subEntityPath));

        assertEquals(1, order.handled, "sub-entity event should still dispatch to root handler");
    }

    /**
     * 路由拒绝测试专用：普通 {@link EntityId}（非 {@link AggregateRootId}）。
     */
    static final class PlainId implements EntityId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Plain");

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>PlainId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        PlainId(String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PlainId)) {
                return false;
            }
            PlainId other = (PlainId) o;
            return Objects.equals(this.value, other.value);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "PlainId{" + "value=" + value + "}";
        }
    }

    /**
     * 合法聚合根标识。
     */
    static final class RootId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");

        /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>RootId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        RootId(String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RootId)) {
                return false;
            }
            RootId other = (RootId) o;
            return Objects.equals(this.value, other.value);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "RootId{" + "value=" + value + "}";
        }
    }

    /**
     * 路径事件：携带任意形态的实体标识路径，处理器统一存在，
     * 保证异常只能来自首段断言而非「找不到处理器」。
     */
    static final class PathEvent extends DomainEvent<EntityId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>PathEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
         */
        PathEvent(EntityIdPath path) {
            super(path);
        }
    }

    /**
     * 测试聚合：为 {@link PathEvent} 提供处理器，统计派发次数。
     */
    static final class Order extends AggregateRoot<String> {

        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        private final String orderId;

        /** \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002 */
        int handled;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Order</code> \u5b9e\u4f8b\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         */
        Order(String orderId) {
            this.orderId = orderId;
        }

        /**
         * \u8bfb\u53d6\u8ba2\u5355\u6807\u8bc6\u3002
         * @return \u8ba2\u5355\u6807\u8bc6
         */
        @Override
        public String id() {
            return orderId;
        }

        /**
         * \u8bb0\u5f55\u805a\u5408\u5bf9\u4e00\u6761\u8def\u5f84\u4e8b\u4ef6\u7684\u5904\u7406\u6b21\u6570\u3002
         * @param event \u7531\u805a\u5408\u6216\u76d1\u542c\u5668\u6536\u5230\u7684\u6d4b\u8bd5\u4e8b\u4ef6
         */
        @EventHandler
        void on(PathEvent event) {
            this.handled++;
        }
    }
}
