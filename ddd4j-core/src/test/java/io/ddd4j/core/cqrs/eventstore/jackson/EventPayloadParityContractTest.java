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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线共享的事件载荷 JSON 契约；构造器参数由各线 Jackson 版本决定。 */
class EventPayloadParityContractTest {

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

    @Test
    void malformedPayloadKeepsTheOriginalCause() throws Exception {
        EventPayloadSerializer serializer = serializerForCurrentJacksonLine();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"fact\":", SampleEvent.class));
        assertEquals("Failed to deserialize event", failure.getMessage());
        assertNotNull(failure.getCause());
    }

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

    /** 可通过 Jackson 无参构造器恢复的领域事件。 */
    public static final class SampleEvent extends DomainEvent<StringEntityId> {
        private String fact;

        public SampleEvent() {
            super();
        }

        private SampleEvent(String fact) {
            super("order-1");
            this.fact = fact;
        }

        public String getFact() {
            return fact;
        }

        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
