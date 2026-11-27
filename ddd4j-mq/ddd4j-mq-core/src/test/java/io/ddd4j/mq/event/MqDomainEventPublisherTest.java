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
package io.ddd4j.mq.event;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.mq.MQProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MqDomainEventPublisher} 单测。
 *
 * <p>验证领域事件到 MQ 事件的转换正确性（topic / tag / payload / tenantId / msgId），
 * 以及非领域事件和 null 的防御行为。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MqDomainEventPublisherTest {

    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    private static final String ENTITY_ID = "order-001";

    /** \u6d4b\u8bd5\u8fd0\u884c\u65f6\u7528\u4e8e\u63a5\u6536\u5f85\u53d1\u5e03\u6d88\u606f\u6216\u9886\u57df\u4e8b\u4ef6\u7684\u53d1\u5e03\u5668\u3002 */
    private final MqDomainEventPublisher publisher = new MqDomainEventPublisher();
    /** \u4e8b\u4ef6\u53d1\u5e03\u5668\u5b9e\u9645\u6536\u5230\u7684\u4e8b\u4ef6\uff0c\u4f9b\u540e\u7eed\u65ad\u8a00\u4f7f\u7528\u3002 */
    private final List<MQEvent> publishedEvents = new ArrayList<>();
    /** \u6d4b\u8bd5\u671f\u95f4\u7684\u4ee3\u7406\u53d1\u5e03\u5668\u6ce8\u518c\u8868\u3002 */
    private final Map<String, Consumer<MQEvent>> publisherMap = new ConcurrentHashMap<>();

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6e05\u9664<code>publisherMap</code>\u3001\u6e05\u9664<code>publishedEvents</code>\u3002 */
    @BeforeEach
    void setUp() {
        publisherMap.clear();
        publishedEvents.clear();
        publisherMap.put("test", event -> publishedEvents.add(event));
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publisherMap);
        BaseContext.inject(MQEvent.MQ_PROPERTIES, new MQProperties());
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u79fb\u9664<code>BaseContext</code>\u3002 */
    @AfterEach
    void tearDown() {
        BaseContext.remove(MQEvent.MQ_EVENT_PUBLISHER);
        BaseContext.remove(MQEvent.MQ_PROPERTIES);
    }

    // ========================= publish(DomainEvent) =========================

    /** \u9a8c\u8bc1<code>publishedEvents.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>mqEvent.getTopic()</code> \u7b49\u4e8e <code>"TestDomainEvent"</code>\u3002 */
    @Test
    void publishDomainEvent_shouldConvertToMqEventWithCorrectTopic() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        assertEquals(1, publishedEvents.size());
        MQEvent mqEvent = publishedEvents.get(0);
        assertEquals("TestDomainEvent", mqEvent.getTopic());
    }

    /** \u9a8c\u8bc1<code>mqEvent.getTag()</code> \u7b49\u4e8e <code>MqDomainEventPublisher.DOMAIN_EVENT_TAG</code>\u3002 */
    @Test
    void publishDomainEvent_shouldSetDomainEventTag() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertEquals(MqDomainEventPublisher.DOMAIN_EVENT_TAG, mqEvent.getTag());
    }

    /** \u9a8c\u8bc1<code>carrier.getPayload()</code> \u7b49\u4e8e <code>"payload:" + ENTITY_ID</code>\u3002 */
    @Test
    void publishDomainEvent_shouldSerializePayloadAsJson() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        MqDomainEventPublisher deterministicPublisher = new MqDomainEventPublisher(new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S src, Class<T> dist) {
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object src) {
                return (T) ("payload:" + ((DomainEvent<?>) src).source());
            }
        });

        deterministicPublisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertInstanceOf(DomainEventCarrier.class, mqEvent);
        DomainEventCarrier carrier = (DomainEventCarrier) mqEvent;

        assertEquals("payload:" + ENTITY_ID, carrier.getPayload());
    }

    /** \u9a8c\u8bc1<code>carrier.getDomainEventType()</code> \u7b49\u4e8e <code>TestDomainEvent.class.getName()</code>\u3002 */
    @Test
    void publishDomainEvent_shouldCarryDomainEventTypeClassName() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        DomainEventCarrier carrier = (DomainEventCarrier) publishedEvents.get(0);
        assertEquals(TestDomainEvent.class.getName(), carrier.getDomainEventType());
    }

    /** \u9a8c\u8bc1<code>domainEvent.getEventId()</code>\u975e\u7a7a\uff0c<code>mqEvent.getMsgId()</code> \u7b49\u4e8e <code>domainEvent.getEventId().asString()</code>\u3002 */
    @Test
    void publishDomainEvent_shouldUseEventIdAsMsgId() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertNotNull(domainEvent.getEventId());
        assertEquals(domainEvent.getEventId().asString(), mqEvent.getMsgId());
    }

    /** \u9a8c\u8bc1<code>mqEvent.getTenantId()</code> \u7b49\u4e8e <code>"tenant-abc"</code>\u3002 */
    @Test
    void publishDomainEvent_shouldPreserveTenantId() {
        io.ddd4j.core.context.ThreadContext.set(
                io.ddd4j.core.constant.ContextConstants.TENANT_ID, "tenant-abc");
        try {
            TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

            publisher.publish(domainEvent);

            MQEvent mqEvent = publishedEvents.get(0);
            assertEquals("tenant-abc", mqEvent.getTenantId());
        } finally {
            io.ddd4j.core.context.ThreadContext.clear();
        }
    }

    // ========================= publish(Object) =========================

    /** \u9a8c\u8bc1<code>publishedEvents.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>publishedEvents.get(0).getTopic()</code> \u7b49\u4e8e <code>"TestDomainEvent"</code>\u3002 */
    @Test
    void publishObject_withDomainEvent_shouldDelegateToPublishDomainEvent() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish((Object) domainEvent);

        assertEquals(1, publishedEvents.size());
        assertEquals("TestDomainEvent", publishedEvents.get(0).getTopic());
    }

    /** \u9a8c\u8bc1<code>publishedEvents.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void publishObject_withNonDomainEvent_shouldNotSendToMq() {
        publisher.publish("not a domain event");

        assertTrue(publishedEvents.isEmpty(),
                "非 DomainEvent 对象不应发送到 MQ");
    }

    /** \u9a8c\u8bc1<code>publishedEvents.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void publishObject_withNull_shouldNotThrowAndNotSend() {
        assertDoesNotThrow(() -> publisher.publish((Object) null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= publish(null DomainEvent) =========================

    /** \u9a8c\u8bc1<code>publishedEvents.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void publishDomainEvent_withNull_shouldNotThrowAndNotSend() {
        assertDoesNotThrow(() -> publisher.publish((DomainEvent<?>) null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= publishAll =========================

    /** \u9a8c\u8bc1<code>publishedEvents.size()</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void publishAll_shouldPublishEachEvent() {
        TestDomainEvent event1 = new TestDomainEvent("order-001");
        TestDomainEvent event2 = new TestDomainEvent("order-002");

        publisher.publishAll(Arrays.asList(event1, event2));

        assertEquals(2, publishedEvents.size());
    }

    /** \u9a8c\u8bc1<code>publishedEvents.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void publishAll_withNull_shouldNotThrow() {
        assertDoesNotThrow(() -> publisher.publishAll(null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= toCarrier（直接验证转换） =========================

    /** \u9a8c\u8bc1<code>carrier.getTopic()</code> \u7b49\u4e8e <code>"TestDomainEvent"</code>\u3002 */
    @Test
    void toCarrier_shouldMapEventTypeToTopic() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        DomainEventCarrier carrier = publisher.toCarrier(domainEvent);

        assertEquals("TestDomainEvent", carrier.getTopic());
    }

    /** \u9a8c\u8bc1<code>carrier.getTag()</code> \u7b49\u4e8e <code>MqDomainEventPublisher.DOMAIN_EVENT_TAG</code>\u3002 */
    @Test
    void toCarrier_shouldSetDomainEventTag() {
        DomainEventCarrier carrier = publisher.toCarrier(new TestDomainEvent(ENTITY_ID));

        assertEquals(MqDomainEventPublisher.DOMAIN_EVENT_TAG, carrier.getTag());
    }

    /** \u9a8c\u8bc1<code>carrier.getCorrelationId()</code> \u7b49\u4e8e <code>causing.getEventId().asString()</code>\uff0c<code>carrier.getCausationId()</code> \u7b49\u4e8e <code>causing.getEventId().asString()</code>\u3002 */
    @Test
    void toCarrier_shouldPreserveCorrelationAndCausationIds() {
        TestDomainEvent causing = new TestDomainEvent(ENTITY_ID);
        TestDomainEvent child = new TestDomainEvent(ENTITY_ID, causing);

        DomainEventCarrier carrier = publisher.toCarrier(child);

        assertEquals(causing.getEventId().asString(), carrier.getCorrelationId());
        assertEquals(causing.getEventId().asString(), carrier.getCausationId());
    }

    // ========================= 测试辅助类 =========================

    /**
     * 测试用领域事件。
     */
    static class TestDomainEvent extends DomainEvent<StringEntityId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestDomainEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>TestDomainEvent</code> \u7684 <code>String</code> \u503c
         */
        TestDomainEvent(String entityId) {
            super(entityId);
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestDomainEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>TestDomainEvent</code> \u7684 <code>String</code> \u503c
         * @param causing \u7528\u4e8e\u521d\u59cb\u5316 <code>TestDomainEvent</code> \u7684 <code>DomainEvent&lt;?&gt;</code> \u503c
         */
        TestDomainEvent(String entityId, DomainEvent<?> causing) {
            super(new EntityIdPath(new StringEntityId(entityId)), causing);
        }
    }
}
