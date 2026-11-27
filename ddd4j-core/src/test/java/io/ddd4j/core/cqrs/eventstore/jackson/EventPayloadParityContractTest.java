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
package io.ddd4j.core.cqrs.eventstore.jackson;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线共享的事件载荷 JSON 契约；构造器参数由各线 Jackson 版本决定。
 */
class EventPayloadParityContractTest {

    /**
     * 通过 <code>(EventPayloadSerializer)constructor.newInstance(mapper)</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>(EventPayloadSerializer)constructor.newInstance(mapper)</code>
     * @throws Exception 反射访问被测成员时失败
     */
    private static EventPayloadSerializer serializerForCurrentJacksonLine() throws Exception {
        Object mapper = JsonKit.defaultObjectMapper();
        try {
            mapper.getClass().getMethod("findAndRegisterModules").invoke(mapper);
        } catch (NoSuchMethodException ignored) {
            // Jackson 3 内置 Java Time 支持，不提供 Jackson 2 的模块发现方法。
        }
        Constructor<?> constructor = EventPayloadSerializer.class.getConstructors()[0];
        return (EventPayloadSerializer) constructor.newInstance(mapper);
    }

    /**
     * 验证<code>json.contains("\"&#64;class\"")</code>为假，<code>restored instanceof SampleEvent</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void explicitEventTypeIgnoresUnknownAndUntrustedClassMarkers() throws Exception {
        EventPayloadSerializer serializer = serializerForCurrentJacksonLine();
        SampleEvent original = new SampleEvent("created");
        String json = serializer.serialize(original);
        assertTrue(json.contains("\"fact\":\"created\""));
        assertFalse(json.contains("\"@class\""));

        String withFutureFields = json.substring(0, json.length() - 1)
                + ",\"futureProperty\":\"newer-writer\",\"@class\":\"java.lang.ProcessBuilder\"}";
        DomainEvent<?> restored = serializer.deserialize(withFutureFields, SampleEvent.class);
        assertTrue(restored instanceof SampleEvent);
        assertEquals("created", ((SampleEvent) restored).getFact());
        assertEquals(original.getEventId(), restored.getEventId());
    }

    /**
     * 验证<code>serializer.deserialize("{\"fact\":", SampleEvent.class)</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage()</code> 等于 <code>"Failed to deserialize event"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void malformedPayloadKeepsTheOriginalCause() throws Exception {
        EventPayloadSerializer serializer = serializerForCurrentJacksonLine();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"fact\":", SampleEvent.class));
        assertEquals("Failed to deserialize event", failure.getMessage());
        assertNotNull(failure.getCause());
    }

    /**
     * 可通过 Jackson 无参构造器恢复的领域事件。
     */
    public static final class SampleEvent extends DomainEvent<StringEntityId> {
        /**
         * 当前测试持久化或读取的业务事实样本。
         */
        private String fact;

        /**
         * 使用 <code>super()</code> 初始化 <code>SampleEvent</code> 测试夹具。
         */
        public SampleEvent() {
            super();
        }

        /**
         * 设置 <code>fact</code>，用于构造 <code>SampleEvent</code> 测试实例。
         *
         * @param fact 用于初始化 <code>SampleEvent</code> 的 <code>String</code> 值
         */
        private SampleEvent(String fact) {
            super("order-1");
            this.fact = fact;
        }

        /**
         * 读取<code>fact</code> 字段。
         *
         * @return <code>fact</code> 字段
         */
        public String getFact() {
            return fact;
        }

        /**
         * 设置<code>fact</code> 字段。
         *
         * @param fact 要写入的值
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
