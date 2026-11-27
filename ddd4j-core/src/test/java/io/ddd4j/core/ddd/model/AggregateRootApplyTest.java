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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AggregateRoot#apply(DomainEvent)} 与 {@link AggregateRoot#loadFromHistory(List)}
 * 的反射派发行为测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class AggregateRootApplyTest {

    /** \u9a8c\u8bc1<code>applied</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>event</code>\uff0c<code>order.status</code> \u7b49\u4e8e <code>"CREATED"</code>\uff0c<code>order.domainEvents().contains(event)</code>\u4e3a\u771f\u3002 */
    @Test
    void applyInvokesAnnotatedHandlerAndRegistersEvent() {
        Order order = new Order("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent();

        OrderCreatedEvent applied = order.apply(event);

        assertSame(event, applied, "apply should return the applied event");
        assertEquals("CREATED", order.status, "@EventHandler method should mutate aggregate state");
        assertTrue(order.domainEvents().contains(event), "applied event should be registered in domainEvents");
    }

    /** \u9a8c\u8bc1<code>order.apply(new UnhandledEvent())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exception.getMessage().contains(UnhandledEvent.class.getName())</code>\u4e3a\u771f\uff0c<code>exception.getMessage().contains("rooted at Order:order-1")</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>order.status</code> \u7b49\u4e8e <code>"CREATED"</code>\uff0c<code>order.domainEvents().isEmpty()</code>\u4e3a\u771f\uff0c<code>order.hasDomainEvents()</code>\u4e3a\u5047\u3002 */
    @Test
    void loadFromHistoryRebuildsAggregateWithoutEnqueueing() {
        Order order = new Order("order-1");

        order.loadFromHistory(Arrays.asList(new OrderCreatedEvent(), new OrderCreatedEvent()));

        assertEquals("CREATED", order.status, "replay should rebuild aggregate state");
        assertTrue(order.domainEvents().isEmpty(), "replay must not enqueue events into domainEvents");
        assertFalse(order.hasDomainEvents());
    }

    /** \u9a8c\u8bc1<code>applying.notified</code>\u4e3a\u771f\uff0c<code>replaying.status</code> \u7b49\u4e8e <code>"CREATED"</code>\uff0c<code>replaying.notified</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>order.status</code>\u4e3a\u7a7a\uff0c<code>order.domainEvents().isEmpty()</code>\u4e3a\u771f\u3002 */
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

        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        private final String orderId;

        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        String status;

        /** \u8bb0\u5f55\u901a\u77e5\u56de\u8c03\u5df2\u7ecf\u88ab\u89e6\u53d1\u7684\u89c2\u5bdf\u72b6\u6001\u3002 */
        boolean notified;

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
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.status = "CREATED"</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @EventHandler
        void on(OrderCreatedEvent event) {
            this.status = "CREATED";
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.notified = true</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @EventHandler(ignoreOnReplay = true)
        void on(OrderNotifiedEvent event) {
            this.notified = true;
        }
    }

    /** \u8868\u793a\u8ba2\u5355\u521b\u5efa\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u9a8c\u8bc1\u4e8b\u4ef6\u5904\u7406\u94fe\u3002 */
    static class OrderCreatedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderCreatedEvent() {
            super(new EntityIdPath(new OrderId("order-1")));
        }

        /** \u8ba2\u5355\u6d4b\u8bd5\u805a\u5408\u7684\u6807\u8bc6\u7c7b\u578b\u3002 */
        final static class OrderId implements AggregateRootId {

            /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
            private static final long serialVersionUID = 0L;

            /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
            private final String value;

            /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
            private static final EntityType TYPE = new StringEntityType("Order");

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

            /**
             * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>OrderId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
             * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
             */
            @JsonCreator()
            OrderId(@JsonProperty("value") String value) {
                this.value = value;
            }

            /**
             * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
             * @return <code>value</code> \u5b57\u6bb5
             */
            @JsonProperty("value")
            public String value() {
                return value;
            }

            /** {@inheritDoc} */
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

            /** {@inheritDoc} */
            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(value);
                return result;
            }

            /** {@inheritDoc} */
            @Override
            public String toString() {
                return "OrderId[value=" + value + "]";
            }
        }
    }

    /** \u4f9b\u4f20\u8f93\u3001\u5e8f\u5217\u5316\u6216\u5904\u7406\u5668\u5206\u53d1\u9a8c\u8bc1\u7684\u9886\u57df\u4e8b\u4ef6\u6837\u672c\u3002 */
    static class OrderNotifiedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderNotifiedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderNotifiedEvent() {
            super(new EntityIdPath(new OrderCreatedEvent.OrderId("order-1")));
        }
    }

    /** \u672a\u6ce8\u518c\u5904\u7406\u5668\u7684\u4e8b\u4ef6\u6837\u672c\uff0c\u7528\u4e8e\u9a8c\u8bc1\u8df3\u8fc7\u6216\u5206\u53d1\u7ed3\u679c\u3002 */
    static class UnhandledEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>UnhandledEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        UnhandledEvent() {
            super(new EntityIdPath(new OrderCreatedEvent.OrderId("order-1")));
        }
    }
}
