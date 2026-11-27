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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventPayloadSerializer} \u5951\u7ea6\u6d4b\u8bd5\uff1around-trip\u3001\u6e90 mapper \u9694\u79bb\u3001\u5f02\u5e38\u5305\u88c5\u3001
 *  \u9ed8\u8ba4\u4e25\u683c\u6a21\u5f0f\u4e0b\u53cd\u5e8f\u5217\u5316\uff08\u542b entity-id-path/event-type \u56de\u8bfb\uff09\u3002
 *
 *  <p>\u6ce8\u610f\uff1a\u672c\u7248\u672c\u4e0d\u518d\u4f7f\u7528 {@code activateDefaultTyping} \u591a\u6001\u6807\u8bb0\u2014\u2014
 *  JSON \u8f93\u51fa<b>\u4e0d\u542b</b>{@code @class}\uff0c\u53cd\u5e8f\u5217\u5316\u7aef\u5fc5\u987b\u901a\u8fc7
 *  {@link EventPayloadSerializer#deserialize(String, Class)} \u663e\u5f0f\u4f20\u5165\u76ee\u6807\u7c7b\u578b\u3002
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class EventPayloadSerializerTest {

    /** 用于编解码领域事件回归样本的 Jackson 映射器。 */
    private final JsonMapper sourceMapper = JsonMapper.builder().findAndAddModules().build();
    /** \u5e8f\u5217\u5316\u9886\u57df\u4e8b\u4ef6\u6216\u6062\u590d\u539f\u59cb\u5bf9\u8c61\u7684\u6d4b\u8bd5\u7ec4\u4ef6\u3002 */
    private final EventPayloadSerializer serializer = new EventPayloadSerializer(sourceMapper);

    /** \u9a8c\u8bc1<code>json</code> \u5305\u542b <code>"\"event-id\""</code>\uff0c<code>json</code> \u5305\u542b <code>"\"event-timestamp\""</code>\uff0c<code>json</code> \u4e0d\u5305\u542b <code>"\"aggregateVersion\""</code>\u3002 */
    @Test
    void serializeShouldCarryBusinessAndEventMetadata() {
        String json = serializer.serialize(new OrderCreatedEvent("order-created"));
        assertTrue(json.contains("\"fact\":\"order-created\""));
        assertTrue(json.contains("\"event-id\""));
        assertTrue(json.contains("\"event-timestamp\""));
        assertTrue(json.contains("\"entity-id-path\""));
        assertFalse(json.contains("eventId"));
        assertFalse(json.contains("eventTimestamp"));
        assertFalse(json.contains("entityIdPath"));
        assertFalse(json.contains("aggregateVersion"));
    }

    /** \u9a8c\u8bc1<code>restored</code> \u7684\u7c7b\u578b\u4e3a <code>OrderPlacedEvent.class</code>\uff0c<code>((OrderPlacedEvent) restored).getProductName()</code> \u7b49\u4e8e <code>"thinkpad"</code>\uff0c<code>restored.getEventTimestamp().toInstant()</code> \u7b49\u4e8e <code>originalTimestamp.toInstant()</code>\u3002 */
    @Test
    void roundTripRestoresConcreteEventWithZonedDateTimeTimestamp() {
        OrderCreatedEvent original = new OrderCreatedEvent("order-created");
        ZonedDateTime originalTimestamp = original.getEventTimestamp();

        DomainEvent<?> restored = serializer.deserialize(serializer.serialize(original), OrderCreatedEvent.class);

        assertEquals(original.getEventId(), restored.getEventId());
        assertEquals(originalTimestamp.toInstant(), restored.getEventTimestamp().toInstant());
        assertEquals("order-1", restored.source());
        assertEquals("order-created", ((OrderCreatedEvent) restored).getFact());
    }

    /** \u9a8c\u8bc1<code>restored.getEntityIdPath().asString()</code> \u7b49\u4e8e <code>"String:order-2"</code>\uff0c<code>restored.getEntityIdPath().last().asString()</code> \u7b49\u4e8e <code>"order-2"</code>\u3002 */
    @Test
    void roundTripPreservesEntityIdPath() {
        OrderCreatedEvent original = new OrderCreatedEvent("order-created", "order-2");

        OrderCreatedEvent restored = (OrderCreatedEvent) serializer.deserialize(
                serializer.serialize(original), OrderCreatedEvent.class);

        // TestId 未注册到 EntityIdRegistry，回读使用保留值的 StringEntityId。
        assertEquals("String:order-2", restored.getEntityIdPath().asString());
        assertEquals("order-2", restored.getEntityIdPath().last().asString());
    }

    /** \u9a8c\u8bc1<code>restored.getEventType().asString()</code> \u7b49\u4e8e <code>"OrderPlacedEvent"</code>\u3002 */
    @Test
    void roundTripPreservesEventType() {
        OrderCreatedEvent original = new OrderCreatedEvent("order-created");

        OrderCreatedEvent restored = (OrderCreatedEvent) serializer.deserialize(
                serializer.serialize(original), OrderCreatedEvent.class);

        assertEquals("OrderCreatedEvent", restored.getEventType().asString());
    }

    /** \u9a8c\u8bc1<code>json</code> \u4e0d\u5305\u542b <code>"\"&#64;class\""</code>\u3002 */
    @Test
    void serializedJsonDoesNotCarryPolymorphicClassMarker() {
        String json = serializer.serialize(new OrderCreatedEvent("order-created"));
        assertFalse(json.contains("\"@class\""));
    }

    /** \u9a8c\u8bc1<code>json</code> \u5305\u542b <code>"\"correlation-id\""</code>\uff0c<code>json</code> \u4e0d\u5305\u542b <code>"correlationId"</code>\u3002 */
    @Test
    void causalityMetadataUsesHyphenatedPayloadFields() {
        OrderCreatedEvent cause = new OrderCreatedEvent("cause");
        OrderCreatedEvent effect = new OrderCreatedEvent("effect", cause);
        String json = serializer.serialize(effect);
        assertTrue(json.contains("\"correlation-id\""));
        assertTrue(json.contains("\"causation-id\""));
        assertFalse(json.contains("correlationId"));
        assertFalse(json.contains("causationId"));
    }

    /**
     * \u9a8c\u8bc1<code>plainJson</code> \u4e0d\u5305\u542b <code>"&#64;class"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void constructionLeavesSourceMapperUntouched() throws Exception {
        serializer.serialize(new OrderCreatedEvent("order-created"));
        String plainJson = sourceMapper.writeValueAsString(new OrderCreatedEvent("order-created"));
        assertFalse(plainJson.contains("\"@class\""));
        assertTrue(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    /** \u9a8c\u8bc1<code>sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>\u4e3a\u771f\u3002 */
    @Test
    void mapperKeepsDefaultStrictUnknownPropertyHandling() {
        assertTrue(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    /** \u9a8c\u8bc1<code>restored.getProductName()</code> \u7b49\u4e8e <code>"thinkpad"</code>\uff0c<code>restored.getQuantity()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)</code>\u4e3a\u771f\u3002 */
    @Test
    void deserializeIgnoresUnknownPayloadPropertyWithoutChangingSourceMapper() {
        String payload = serializer.serialize(new OrderCreatedEvent("ok"));
        String withFutureProperty = payload.substring(0, payload.length() - 1)
                + ",\"futureProperty\":\"newer-writer\"}";

        DomainEvent<?> event = serializer.deserialize(withFutureProperty, OrderCreatedEvent.class);

        assertEquals("ok", ((OrderCreatedEvent) event).getFact());
        assertTrue(sourceMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    /** \u9a8c\u8bc1<code>restored</code> \u7684\u7c7b\u578b\u4e3a <code>OrderPlacedEvent.class</code>\uff0c<code>((OrderPlacedEvent) restored).getProductName()</code> \u7b49\u4e8e <code>"thinkpad"</code>\u3002 */
    @Test
    void unknownClassMarkerCannotChooseTheDeserializedType() {
        String payload = serializer.serialize(new OrderCreatedEvent("order-created"));
        String withClassMarker = payload.substring(0, payload.length() - 1)
                + ",\"@class\":\"java.lang.ProcessBuilder\"}";

        DomainEvent<?> restored = serializer.deserialize(withClassMarker, OrderCreatedEvent.class);

        assertInstanceOf(OrderCreatedEvent.class, restored);
        assertEquals("order-created", ((OrderCreatedEvent) restored).getFact());
    }

    /** \u9a8c\u8bc1<code>serializer.deserialize("{\"productName\":", OrderPlacedEvent.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exception</code> \u7684\u5f02\u5e38\u6d88\u606f\u4e3a <code>"Failed to deserialize event"</code>\u3002 */
    @Test
    void deserializeThrowsIllegalStateExceptionOnMalformedJson() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> serializer.deserialize("{\"fact\":", OrderCreatedEvent.class));

        assertEquals("Failed to deserialize event", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullSourceMapper() {
        assertThrows(NullPointerException.class, () -> new EventPayloadSerializer(null));
    }

    /** 为事件序列化回归提供固定类型的测试聚合标识。 */
    private static final class TestId implements AggregateRootId {
        /** 保存 <code>TYPE</code> 对应的 <code>EntityType</code> 数据。 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** 保存 <code>value</code> 对应的 <code>String</code> 数据。 */
        private final String value;

        /**
         * 用给定值构造领域事件序列化测试使用的聚合标识。
         *
         * @param value 由当前成员保存或用于计算的值
         */
        private TestId(String value) {
            this.value = value;
        }

        /**
         * 返回测试聚合标识的类型前缀。
         *
         * @return 返回表达式 <code>TYPE</code>
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回不含类型前缀的聚合标识字符串。
         *
         * @return 返回表达式 <code>value</code>
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回包含类型前缀的聚合标识字符串。
         *
         * @return 返回表达式 <code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }

    /**
     * 业务事件样例：无参构造 + JavaBean 属性（Jackson 往返约定）。
     */
    public static final class OrderCreatedEvent extends DomainEvent<TestId> {
        /** 保存 <code>fact</code> 对应的 <code>String</code> 数据。 */
        private String fact;

        /** 初始化 <code>OrderCreatedEvent</code> 并委托其默认构造路径。 */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * 初始化 <code>OrderCreatedEvent</code> 并委托其默认构造路径。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        private OrderCreatedEvent(String fact) {
            this(fact, "order-1");
        }

        /**
         * 初始化 <code>OrderCreatedEvent</code> 使用的 <code>fact</code>。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         * @param id 传入此操作的 <code>String</code> 类型 <code>id</code>
         */
        private OrderCreatedEvent(String fact, String id) {
            super(new EntityIdPath(new TestId(id)));
            this.fact = fact;
        }

        /**
         * 初始化 <code>OrderCreatedEvent</code> 使用的 <code>fact</code>。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         * @param causingEvent 传入此操作的 <code>Event</code> 类型 <code>causingEvent</code>
         */
        private OrderCreatedEvent(String fact, Event causingEvent) {
            super(new EntityIdPath(new TestId("order-1")), causingEvent);
            this.fact = fact;
        }

        /**
         * 读取<code>fact</code> 的当前值。
         *
         * @return 返回表达式 <code>fact</code>
         */
        public String getFact() {
            return fact;
        }

        /**
         * 执行 <code>set Fact</code> 对应的处理。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
