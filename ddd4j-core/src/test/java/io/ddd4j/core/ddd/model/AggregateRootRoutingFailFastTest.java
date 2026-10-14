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

    @Test
    void applyRejectsPlainEntityIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new PathEvent(new EntityIdPath(new PlainId("order-1")))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "fail-fast message should name the expected root segment type");
    }

    @Test
    void applyRejectsStringEntityIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new PathEvent(new EntityIdPath(new StringEntityId("order-1")))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "StringEntityId convenience root segment must also be rejected");
    }

    @Test
    void loadFromHistoryRejectsNonAggregateRootIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.loadFromHistory(Arrays.asList(new PathEvent(new EntityIdPath(new PlainId("order-1"))))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "replay path must fail fast on the same assertion");
    }

    @Test
    void applyAcceptsAggregateRootIdRootSegment() {
        Order order = new Order("order-1");
        PathEvent event = new PathEvent(new EntityIdPath(new RootId("order-1")));

        order.apply(event);

        assertEquals(1, order.handled, "single-segment path should dispatch to root handler");
        assertTrue(order.domainEvents().contains(event), "applied event should be registered");
    }

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

        private static final EntityType TYPE = new StringEntityType("Plain");

        private final String value;

        PlainId(String value) {
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
            return TYPE.asString() + ":" + value;
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "PlainId{" + "value=" + value + "}";
        }
    }

    /**
     * 合法聚合根标识。
     */
    static final class RootId implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("Order");

        private final String value;

        RootId(String value) {
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
            return TYPE.asString() + ":" + value;
        }

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

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

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

        PathEvent(EntityIdPath path) {
            super(path);
        }
    }

    /**
     * 测试聚合：为 {@link PathEvent} 提供处理器，统计派发次数。
     */
    static final class Order extends AggregateRoot<String> {

        private final String orderId;

        int handled;

        Order(String orderId) {
            this.orderId = orderId;
        }

        @Override
        public String id() {
            return orderId;
        }

        @EventHandler
        void on(PathEvent event) {
            this.handled++;
        }
    }
}
