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

    private static final String ENTITY_ID = "order-001";

    private final MqDomainEventPublisher publisher = new MqDomainEventPublisher();
    private final List<MQEvent> publishedEvents = new ArrayList<>();
    private final Map<String, Consumer<MQEvent>> publisherMap = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        publisherMap.clear();
        publishedEvents.clear();
        publisherMap.put("test", event -> publishedEvents.add(event));
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publisherMap);
        BaseContext.inject(MQEvent.MQ_PROPERTIES, new MQProperties());
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove(MQEvent.MQ_EVENT_PUBLISHER);
        BaseContext.remove(MQEvent.MQ_PROPERTIES);
    }

    // ========================= publish(DomainEvent) =========================

    @Test
    void publishDomainEvent_shouldConvertToMqEventWithCorrectTopic() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        assertEquals(1, publishedEvents.size());
        MQEvent mqEvent = publishedEvents.get(0);
        assertEquals("TestDomainEvent", mqEvent.getTopic());
    }

    @Test
    void publishDomainEvent_shouldSetDomainEventTag() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertEquals(MqDomainEventPublisher.DOMAIN_EVENT_TAG, mqEvent.getTag());
    }

    @Test
    void publishDomainEvent_shouldSerializePayloadAsJson() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertInstanceOf(DomainEventCarrier.class, mqEvent);
        DomainEventCarrier carrier = (DomainEventCarrier) mqEvent;

        // 负载非空且包含领域事件关键字段
        assertNotNull(carrier.getPayload());
        String payload = carrier.getPayload();
        assertTrue(payload.contains("\"event-type\""), "payload 应包含 event-type 字段");
        assertTrue(payload.contains(ENTITY_ID), "payload 应包含实体标识");
    }

    @Test
    void publishDomainEvent_shouldCarryDomainEventTypeClassName() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        DomainEventCarrier carrier = (DomainEventCarrier) publishedEvents.get(0);
        assertEquals(TestDomainEvent.class.getName(), carrier.getDomainEventType());
    }

    @Test
    void publishDomainEvent_shouldUseEventIdAsMsgId() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertNotNull(domainEvent.getEventId());
        assertEquals(domainEvent.getEventId().asString(), mqEvent.getMsgId());
    }

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

    @Test
    void publishObject_withDomainEvent_shouldDelegateToPublishDomainEvent() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish((Object) domainEvent);

        assertEquals(1, publishedEvents.size());
        assertEquals("TestDomainEvent", publishedEvents.get(0).getTopic());
    }

    @Test
    void publishObject_withNonDomainEvent_shouldNotSendToMq() {
        publisher.publish("not a domain event");

        assertTrue(publishedEvents.isEmpty(),
                "非 DomainEvent 对象不应发送到 MQ");
    }

    @Test
    void publishObject_withNull_shouldNotThrowAndNotSend() {
        assertDoesNotThrow(() -> publisher.publish((Object) null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= publish(null DomainEvent) =========================

    @Test
    void publishDomainEvent_withNull_shouldNotThrowAndNotSend() {
        assertDoesNotThrow(() -> publisher.publish((DomainEvent<?>) null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= publishAll =========================

    @Test
    void publishAll_shouldPublishEachEvent() {
        TestDomainEvent event1 = new TestDomainEvent("order-001");
        TestDomainEvent event2 = new TestDomainEvent("order-002");

        publisher.publishAll(List.of(event1, event2));

        assertEquals(2, publishedEvents.size());
    }

    @Test
    void publishAll_withNull_shouldNotThrow() {
        assertDoesNotThrow(() -> publisher.publishAll(null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= toCarrier（直接验证转换） =========================

    @Test
    void toCarrier_shouldMapEventTypeToTopic() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        DomainEventCarrier carrier = publisher.toCarrier(domainEvent);

        assertEquals("TestDomainEvent", carrier.getTopic());
    }

    @Test
    void toCarrier_shouldSetDomainEventTag() {
        DomainEventCarrier carrier = publisher.toCarrier(new TestDomainEvent(ENTITY_ID));

        assertEquals(MqDomainEventPublisher.DOMAIN_EVENT_TAG, carrier.getTag());
    }

    // ========================= 测试辅助类 =========================

    /**
     * 测试用领域事件。
     */
    static class TestDomainEvent extends DomainEvent<StringEntityId> {

        TestDomainEvent(String entityId) {
            super(entityId);
        }
    }
}
