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
     * \u901a\u8fc7 <code>(EventPayloadSerializer)constructor.newInstance(mapper)</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(EventPayloadSerializer)constructor.newInstance(mapper)</code>
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>json.contains("\"&#64;class\"")</code>\u4e3a\u5047\uff0c<code>restored instanceof SampleEvent</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>serializer.deserialize("{\"fact\":", SampleEvent.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage()</code> \u7b49\u4e8e <code>"Failed to deserialize event"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
        /** \u5f53\u524d\u6d4b\u8bd5\u6301\u4e45\u5316\u6216\u8bfb\u53d6\u7684\u4e1a\u52a1\u4e8b\u5b9e\u6837\u672c\u3002 */
        private String fact;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>SampleEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public SampleEvent() {
            super();
        }

        /**
         * \u8bbe\u7f6e <code>fact</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>SampleEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param fact \u7528\u4e8e\u521d\u59cb\u5316 <code>SampleEvent</code> \u7684 <code>String</code> \u503c
         */
        private SampleEvent(String fact) {
            super("order-1");
            this.fact = fact;
        }

        /**
         * \u8bfb\u53d6<code>fact</code> \u5b57\u6bb5\u3002
         * @return <code>fact</code> \u5b57\u6bb5
         */
        public String getFact() {
            return fact;
        }

        /**
         * \u8bbe\u7f6e<code>fact</code> \u5b57\u6bb5\u3002
         * @param fact \u8981\u5199\u5165\u7684\u503c
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
