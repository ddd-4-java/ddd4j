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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class EventHandlerTest {

    /**
     * 验证<code>annotation</code>非空，<code>annotation.ignoreOnReplay()</code>为假。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void annotationIsRuntimeVisible() throws NoSuchMethodException {
        Method method = SampleHandler.class.getDeclaredMethod("onOrderCreated", OrderCreatedEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);

        assertNotNull(annotation, "method should be annotated with @EventHandler");
        assertFalse(annotation.ignoreOnReplay(), "ignoreOnReplay should default to false");
    }

    /**
     * 验证<code>annotation</code>非空，<code>annotation.ignoreOnReplay()</code>为真。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void ignoreOnReplayAttributeIsReadableAtRuntime() throws NoSuchMethodException {
        Method method = SampleHandler.class.getDeclaredMethod("onOrderDiscarded", OrderCreatedEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);

        assertNotNull(annotation, "method should be annotated with @EventHandler");
        assertTrue(annotation.ignoreOnReplay(), "ignoreOnReplay should be readable when set to true");
    }

    /**
     * 验证<code>target</code>非空，<code>retention</code>非空，<code>retention.value()</code> 等于 <code>RetentionPolicy.RUNTIME</code>。
     */
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
         *
         * @param event 事件消息。
         */
        @EventHandler
        public void onOrderCreated(OrderCreatedEvent event) {
        }

        /**
         * onOrderDiscarded() 方法。
         *
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

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        OrderCreatedEvent() {
            super(new EntityIdPath(new OrderId("order-1")));
        }

        /**
         * 订单测试聚合的标识类型。
         */
        final static class OrderId implements EntityId {

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
             * 获取值。
             *
             * @return 方法执行结果。
             */
            @Override
            public EntityType getType() {
                return TYPE;
            }

            /**
             * asString() 方法。
             *
             * @return 结果字符串。
             */
            @Override
            public String asString() {
                return value;
            }

            /**
             * asTypedString() 方法。
             *
             * @return 结果字符串。
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
}
