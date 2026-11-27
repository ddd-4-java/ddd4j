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

import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

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

    /**
     * 验证<code>order.apply(new PathEvent(new EntityIdPath(new PlainId("order-1"))))</code> 抛出 <code>IllegalStateException.class</code>，<code>exception.getMessage().contains("AggregateRootId")</code>为真。
     */
    @Test
    void applyRejectsPlainEntityIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new PathEvent(new EntityIdPath(new PlainId("order-1")))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "fail-fast message should name the expected root segment type");
    }

    /**
     * 验证<code>order.apply(new PathEvent(new EntityIdPath(new StringEntityId("order-1"))))</code> 抛出 <code>IllegalStateException.class</code>，<code>exception.getMessage().contains("AggregateRootId")</code>为真。
     */
    @Test
    void applyRejectsStringEntityIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new PathEvent(new EntityIdPath(new StringEntityId("order-1")))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "StringEntityId convenience root segment must also be rejected");
    }

    /**
     * 验证<code>order.loadFromHistory(Arrays.asList(new PathEvent(new EntityIdPath(new PlainId("order-1")))))</code> 抛出 <code>IllegalStateException.class</code>，<code>exception.getMessage().contains("AggregateRootId")</code>为真。
     */
    @Test
    void loadFromHistoryRejectsNonAggregateRootIdRootSegment() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.loadFromHistory(Arrays.asList(new PathEvent(new EntityIdPath(new PlainId("order-1"))))));

        assertTrue(exception.getMessage().contains("AggregateRootId"),
                "replay path must fail fast on the same assertion");
    }

    /**
     * 验证<code>order.handled</code> 等于 <code>1</code>，<code>order.domainEvents().contains(event)</code>为真。
     */
    @Test
    void applyAcceptsAggregateRootIdRootSegment() {
        Order order = new Order("order-1");
        PathEvent event = new PathEvent(new EntityIdPath(new RootId("order-1")));

        order.apply(event);

        assertEquals(1, order.handled, "single-segment path should dispatch to root handler");
        assertTrue(order.domainEvents().contains(event), "applied event should be registered");
    }

    /**
     * 验证<code>order.handled</code> 等于 <code>1</code>。
     */
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

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Plain");

        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>PlainId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        PlainId(String value) {
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

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * @param o 用来比较的另一个对象，允许为空
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 用<code>value</code>计算哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "PlainId{" + "value=" + value + "}";
        }
    }

    /**
     * 合法聚合根标识。
     */
    static final class RootId implements AggregateRootId {

        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");

        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>RootId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        RootId(String value) {
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

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * @param o 用来比较的另一个对象，允许为空
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 用<code>value</code>计算哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
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
         * 使用 <code>super()</code> 初始化 <code>PathEvent</code> 测试夹具。
         *
         * @param path 测试请求的资源路径或待访问文件路径
         */
        PathEvent(EntityIdPath path) {
            super(path);
        }
    }

    /**
     * 测试聚合：为 {@link PathEvent} 提供处理器，统计派发次数。
     */
    static final class Order extends AggregateRoot<String> {

        /**
         * 订单标识。
         */
        private final String orderId;

        /**
         * 当前测试中已经交给处理器的记录。
         */
        int handled;

        /**
         * 用给定值初始化 <code>Order</code> 实例。
         *
         * @param orderId 订单标识
         */
        Order(String orderId) {
            this.orderId = orderId;
        }

        /**
         * 读取订单标识。
         *
         * @return 订单标识
         */
        @Override
        public String id() {
            return orderId;
        }

        /**
         * 记录聚合对一条路径事件的处理次数。
         *
         * @param event 由聚合或监听器收到的测试事件
         */
        @EventHandler
        void on(PathEvent event) {
            this.handled++;
        }
    }
}
