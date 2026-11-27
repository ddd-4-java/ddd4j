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

package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class EventHandlerTest {

    /**
     * \u9a8c\u8bc1<code>annotation</code>\u975e\u7a7a\uff0c<code>annotation.ignoreOnReplay()</code>\u4e3a\u5047\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void annotationIsRuntimeVisible() throws NoSuchMethodException {
        Method method = SampleHandler.class.getDeclaredMethod("onOrderCreated", OrderCreatedEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);

        assertNotNull(annotation, "method should be annotated with @EventHandler");
        assertFalse(annotation.ignoreOnReplay(), "ignoreOnReplay should default to false");
    }

    /**
     * \u9a8c\u8bc1<code>annotation</code>\u975e\u7a7a\uff0c<code>annotation.ignoreOnReplay()</code>\u4e3a\u771f\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void ignoreOnReplayAttributeIsReadableAtRuntime() throws NoSuchMethodException {
        Method method = SampleHandler.class.getDeclaredMethod("onOrderDiscarded", OrderCreatedEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);

        assertNotNull(annotation, "method should be annotated with @EventHandler");
        assertTrue(annotation.ignoreOnReplay(), "ignoreOnReplay should be readable when set to true");
    }

    /** \u9a8c\u8bc1<code>target</code>\u975e\u7a7a\uff0c<code>retention</code>\u975e\u7a7a\uff0c<code>retention.value()</code> \u7b49\u4e8e <code>RetentionPolicy.RUNTIME</code>\u3002 */
    @Test
    void annotationDeclarationIsMethodTargetedAndRuntimeRetained() {
        Target target = EventHandler.class.getAnnotation(Target.class);
        assertNotNull(target, "@EventHandler must declare @Target");
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, target.value(),
                "@EventHandler must target methods only");

        Retention retention = EventHandler.class.getAnnotation(Retention.class);
        assertNotNull(retention, "@EventHandler must declare @Retention");
        assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                "@EventHandler must be visible at runtime for reflective dispatch");
    }

    /**
     * 测试用事件处理器：携带默认与显式配置的 {@code @EventHandler} 方法。
     */
    static class SampleHandler {

        /**
         * onOrderCreated() 方法。
         * @param event 事件消息。
         */
        @EventHandler
        public void onOrderCreated(OrderCreatedEvent event) {
        }

        /**
         * onOrderDiscarded() 方法。
         * @param event 事件消息。
         */
        @EventHandler(ignoreOnReplay = true)
        public void onOrderDiscarded(OrderCreatedEvent event) {
        }
    }

    /**
     * 测试用订单已创建事件，绑定嵌套的 {@link OrderId} 实体标识。
     */
    static class OrderCreatedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderCreatedEvent() {
            super(new EntityIdPath(new OrderId("order-1")));
        }

        /** \u8ba2\u5355\u6d4b\u8bd5\u805a\u5408\u7684\u6807\u8bc6\u7c7b\u578b\u3002 */
        final static class OrderId implements EntityId {

            /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
            private static final long serialVersionUID = 0L;

            /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
            private final String value;

            /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
            private static final EntityType TYPE = new StringEntityType("Order");

            /**
             * 获取值。
             * @return 方法执行结果。
             */
            @Override
            public EntityType getType() {
                return TYPE;
            }

            /**
             * asString() 方法。
             * @return 结果字符串。
             */
            @Override
            public String asString() {
                return value;
            }

            /**
             * asTypedString() 方法。
             * @return 结果字符串。
             */
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
}
