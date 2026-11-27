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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AggregateRoot#apply(DomainEvent)} 与 {@link AggregateRoot#loadFromHistory(List)}
 * 的反射派发行为测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class AggregateRootApplyTest {

    /**
     * 验证<code>applied</code> 与…为同一实例 <code>event</code>，<code>order.status</code> 等于 <code>"CREATED"</code>，<code>order.domainEvents().contains(event)</code>为真。
     */
    @Test
    void applyInvokesAnnotatedHandlerAndRegistersEvent() {
        Order order = new Order("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent();

        OrderCreatedEvent applied = order.apply(event);

        assertSame(event, applied, "apply should return the applied event");
        assertEquals("CREATED", order.status, "@EventHandler method should mutate aggregate state");
        assertTrue(order.domainEvents().contains(event), "applied event should be registered in domainEvents");
    }

    /**
     * 验证<code>order.apply(new UnhandledEvent())</code> 抛出 <code>IllegalStateException.class</code>，<code>exception.getMessage().contains(UnhandledEvent.class.getName())</code>为真，<code>exception.getMessage().contains("rooted at Order:order-1")</code>为真。
     */
    @Test
    void applyThrowsWhenNoHandlerRegistered() {
        Order order = new Order("order-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.apply(new UnhandledEvent()));

        assertTrue(exception.getMessage().contains(UnhandledEvent.class.getName()),
                "message should name the event type");
        assertTrue(exception.getMessage().contains(Order.class.getName()),
                "message should name the aggregate type");
        assertTrue(exception.getMessage().contains("rooted at Order:order-1"),
                "message should name the aggregate root segment");
    }

    /**
     * 验证<code>order.status</code> 等于 <code>"CREATED"</code>，<code>order.domainEvents().isEmpty()</code>为真，<code>order.hasDomainEvents()</code>为假。
     */
    @Test
    void loadFromHistoryRebuildsAggregateWithoutEnqueueing() {
        Order order = new Order("order-1");

        order.loadFromHistory(Arrays.asList(new OrderCreatedEvent(), new OrderCreatedEvent()));

        assertEquals("CREATED", order.status, "replay should rebuild aggregate state");
        assertTrue(order.domainEvents().isEmpty(), "replay must not enqueue events into domainEvents");
        assertFalse(order.hasDomainEvents());
    }

    /**
     * 验证<code>applying.notified</code>为真，<code>replaying.status</code> 等于 <code>"CREATED"</code>，<code>replaying.notified</code>为假。
     */
    @Test
    void ignoreOnReplayHandlerRunsOnApplyButSkippedOnReplay() {
        Order applying = new Order("order-1");
        applying.apply(new OrderNotifiedEvent());
        assertTrue(applying.notified, "apply should invoke ignoreOnReplay=true handler");

        Order replaying = new Order("order-1");
        replaying.loadFromHistory(Arrays.asList(new OrderCreatedEvent(), new OrderNotifiedEvent()));
        assertEquals("CREATED", replaying.status, "normal handlers should still replay");
        assertFalse(replaying.notified, "loadFromHistory should skip ignoreOnReplay=true handler");
    }

    /**
     * 验证<code>order.status</code>为空，<code>order.domainEvents().isEmpty()</code>为真。
     */
    @Test
    void loadFromHistoryIsNullSafe() {
        Order order = new Order("order-1");

        order.loadFromHistory(null);

        assertNull(order.status);
        assertTrue(order.domainEvents().isEmpty());
    }

    /**
     * 测试聚合：满足 {@link Entity} 契约（实现 {@code id()}），提供两个处理器
     * （一个普通、一个 {@code ignoreOnReplay=true}）。
     */
    static class Order extends AggregateRoot<String> {

        /**
         * 订单标识。
         */
        private final String orderId;

        /**
         * 测试替身返回、模拟或捕获的状态。
         */
        String status;

        /**
         * 记录通知回调已经被触发的观察状态。
         */
        boolean notified;

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
         * 根据本次调用更新 <code>this.status = "CREATED"</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @EventHandler
        void on(OrderCreatedEvent event) {
            this.status = "CREATED";
        }

        /**
         * 根据本次调用更新 <code>this.notified = true</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @EventHandler(ignoreOnReplay = true)
        void on(OrderNotifiedEvent event) {
            this.notified = true;
        }
    }

    /**
     * 表示订单创建的测试领域事件，供断言验证事件处理链。
     */
    static class OrderCreatedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        OrderCreatedEvent() {
            super(new EntityIdPath(new OrderId("order-1")));
        }

        /**
         * 订单测试聚合的标识类型。
         */
        final static class OrderId implements AggregateRootId {

            /**
             * Java 序列化使用的版本标识。
             */
            private static final long serialVersionUID = 0L;
            /**
             * 测试聚合注册和反序列化所用的实体类型标识。
             */
            private static final EntityType TYPE = new StringEntityType("Order");
            /**
             * 组成测试标识的原始值。
             */
            private final String value;

            /**
             * 设置 <code>value</code>，用于构造 <code>OrderId</code> 测试实例。
             *
             * @param value 组成测试标识的原始值
             */
            @JsonCreator()
            OrderId(@JsonProperty("value") String value) {
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
             * 读取<code>value</code> 字段。
             *
             * @return <code>value</code> 字段
             */
            @JsonProperty("value")
            public String value() {
                return value;
            }

            /**
             * 根据<code>value</code>比较对象的值是否相等。
             *
             * <p>只比较运行时类型相同的对象。</p>
             *
             * @param obj 用来比较或转换的输入对象
             * @return 对象满足此实现的相等条件时为真
             */
            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                    return false;
                }
                OrderId other = (OrderId) obj;
                return Objects.equals(this.value, other.value);
            }

            /**
             * 计算与对象相等条件一致的哈希值。
             *
             * @return 由参与相等比较的状态计算出的哈希值
             */
            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(value);
                return result;
            }

            /**
             * 生成用于调试和日志的对象描述，包含<code>value</code>。
             *
             * @return 表示当前对象状态的字符串
             */
            @Override
            public String toString() {
                return "OrderId[value=" + value + "]";
            }
        }
    }

    /**
     * 供传输、序列化或处理器分发验证的领域事件样本。
     */
    static class OrderNotifiedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderNotifiedEvent</code> 测试夹具。
         */
        OrderNotifiedEvent() {
            super(new EntityIdPath(new OrderCreatedEvent.OrderId("order-1")));
        }
    }

    /**
     * 未注册处理器的事件样本，用于验证跳过或分发结果。
     */
    static class UnhandledEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>UnhandledEvent</code> 测试夹具。
         */
        UnhandledEvent() {
            super(new EntityIdPath(new OrderCreatedEvent.OrderId("order-1")));
        }
    }
}
