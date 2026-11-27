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

    /**
     * 构造本组测试输入时复用的固定标识或名称。
     */
    private static final String ENTITY_ID = "order-001";

    /**
     * 测试运行时用于接收待发布消息或领域事件的发布器。
     */
    private final MqDomainEventPublisher publisher = new MqDomainEventPublisher();
    /**
     * 事件发布器实际收到的事件，供后续断言使用。
     */
    private final List<MQEvent> publishedEvents = new ArrayList<>();
    /**
     * 测试期间的代理发布器注册表。
     */
    private final Map<String, Consumer<MQEvent>> publisherMap = new ConcurrentHashMap<>();

    /**
     * 测试前依次清除<code>publisherMap</code>、清除<code>publishedEvents</code>。
     */
    @BeforeEach
    void setUp() {
        publisherMap.clear();
        publishedEvents.clear();
        publisherMap.put("test", event -> publishedEvents.add(event));
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publisherMap);
        BaseContext.inject(MQEvent.MQ_PROPERTIES, new MQProperties());
    }

    /**
     * 测试后依次移除<code>BaseContext</code>。
     */
    @AfterEach
    void tearDown() {
        BaseContext.remove(MQEvent.MQ_EVENT_PUBLISHER);
        BaseContext.remove(MQEvent.MQ_PROPERTIES);
    }

    // ========================= publish(DomainEvent) =========================

    /**
     * 验证<code>publishedEvents.size()</code> 等于 <code>1</code>，<code>mqEvent.getTopic()</code> 等于 <code>"TestDomainEvent"</code>。
     */
    @Test
    void publishDomainEvent_shouldConvertToMqEventWithCorrectTopic() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        assertEquals(1, publishedEvents.size());
        MQEvent mqEvent = publishedEvents.get(0);
        assertEquals("TestDomainEvent", mqEvent.getTopic());
    }

    /**
     * 验证<code>mqEvent.getTag()</code> 等于 <code>MqDomainEventPublisher.DOMAIN_EVENT_TAG</code>。
     */
    @Test
    void publishDomainEvent_shouldSetDomainEventTag() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertEquals(MqDomainEventPublisher.DOMAIN_EVENT_TAG, mqEvent.getTag());
    }

    /**
     * 验证<code>carrier.getPayload()</code> 等于 <code>"payload:" + ENTITY_ID</code>。
     */
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

    /**
     * 验证<code>carrier.getDomainEventType()</code> 等于 <code>TestDomainEvent.class.getName()</code>。
     */
    @Test
    void publishDomainEvent_shouldCarryDomainEventTypeClassName() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        DomainEventCarrier carrier = (DomainEventCarrier) publishedEvents.get(0);
        assertEquals(TestDomainEvent.class.getName(), carrier.getDomainEventType());
    }

    /**
     * 验证<code>domainEvent.getEventId()</code>非空，<code>mqEvent.getMsgId()</code> 等于 <code>domainEvent.getEventId().asString()</code>。
     */
    @Test
    void publishDomainEvent_shouldUseEventIdAsMsgId() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish(domainEvent);

        MQEvent mqEvent = publishedEvents.get(0);
        assertNotNull(domainEvent.getEventId());
        assertEquals(domainEvent.getEventId().asString(), mqEvent.getMsgId());
    }

    /**
     * 验证<code>mqEvent.getTenantId()</code> 等于 <code>"tenant-abc"</code>。
     */
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

    /**
     * 验证<code>publishedEvents.size()</code> 等于 <code>1</code>，<code>publishedEvents.get(0).getTopic()</code> 等于 <code>"TestDomainEvent"</code>。
     */
    @Test
    void publishObject_withDomainEvent_shouldDelegateToPublishDomainEvent() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        publisher.publish((Object) domainEvent);

        assertEquals(1, publishedEvents.size());
        assertEquals("TestDomainEvent", publishedEvents.get(0).getTopic());
    }

    /**
     * 验证<code>publishedEvents.isEmpty()</code>为真。
     */
    @Test
    void publishObject_withNonDomainEvent_shouldNotSendToMq() {
        publisher.publish("not a domain event");

        assertTrue(publishedEvents.isEmpty(),
                "非 DomainEvent 对象不应发送到 MQ");
    }

    /**
     * 验证<code>publishedEvents.isEmpty()</code>为真。
     */
    @Test
    void publishObject_withNull_shouldNotThrowAndNotSend() {
        assertDoesNotThrow(() -> publisher.publish((Object) null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= publish(null DomainEvent) =========================

    /**
     * 验证<code>publishedEvents.isEmpty()</code>为真。
     */
    @Test
    void publishDomainEvent_withNull_shouldNotThrowAndNotSend() {
        assertDoesNotThrow(() -> publisher.publish((DomainEvent<?>) null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= publishAll =========================

    /**
     * 验证<code>publishedEvents.size()</code> 等于 <code>2</code>。
     */
    @Test
    void publishAll_shouldPublishEachEvent() {
        TestDomainEvent event1 = new TestDomainEvent("order-001");
        TestDomainEvent event2 = new TestDomainEvent("order-002");

        publisher.publishAll(Arrays.asList(event1, event2));

        assertEquals(2, publishedEvents.size());
    }

    /**
     * 验证<code>publishedEvents.isEmpty()</code>为真。
     */
    @Test
    void publishAll_withNull_shouldNotThrow() {
        assertDoesNotThrow(() -> publisher.publishAll(null));
        assertTrue(publishedEvents.isEmpty());
    }

    // ========================= toCarrier（直接验证转换） =========================

    /**
     * 验证<code>carrier.getTopic()</code> 等于 <code>"TestDomainEvent"</code>。
     */
    @Test
    void toCarrier_shouldMapEventTypeToTopic() {
        TestDomainEvent domainEvent = new TestDomainEvent(ENTITY_ID);

        DomainEventCarrier carrier = publisher.toCarrier(domainEvent);

        assertEquals("TestDomainEvent", carrier.getTopic());
    }

    /**
     * 验证<code>carrier.getTag()</code> 等于 <code>MqDomainEventPublisher.DOMAIN_EVENT_TAG</code>。
     */
    @Test
    void toCarrier_shouldSetDomainEventTag() {
        DomainEventCarrier carrier = publisher.toCarrier(new TestDomainEvent(ENTITY_ID));

        assertEquals(MqDomainEventPublisher.DOMAIN_EVENT_TAG, carrier.getTag());
    }

    /**
     * 验证<code>carrier.getCorrelationId()</code> 等于 <code>causing.getEventId().asString()</code>，<code>carrier.getCausationId()</code> 等于 <code>causing.getEventId().asString()</code>。
     */
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
         * 使用 <code>super()</code> 初始化 <code>TestDomainEvent</code> 测试夹具。
         *
         * @param entityId 用于初始化 <code>TestDomainEvent</code> 的 <code>String</code> 值
         */
        TestDomainEvent(String entityId) {
            super(entityId);
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TestDomainEvent</code> 测试夹具。
         *
         * @param entityId 用于初始化 <code>TestDomainEvent</code> 的 <code>String</code> 值
         * @param causing  用于初始化 <code>TestDomainEvent</code> 的 <code>DomainEvent&lt;?&gt;</code> 值
         */
        TestDomainEvent(String entityId, DomainEvent<?> causing) {
            super(new EntityIdPath(new StringEntityId(entityId)), causing);
        }
    }
}
