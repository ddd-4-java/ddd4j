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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
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
 * MQ 因果元数据 broker header 双写行为测试。
 *
 * <p>对应 OpenSpec change {@code promote-causality-to-broker-headers}，
 * 派生自家族规格 Requirement: Causality in broker headers：
 * header 权威（非 Java 消费方可直接提取）、payload 内嵌字段迁移期镜像、双写同源一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQCausalityBrokerHeaderMustTest {

    /** \u6d4b\u8bd5\u8fd0\u884c\u65f6\u7528\u4e8e\u63a5\u6536\u5f85\u53d1\u5e03\u6d88\u606f\u6216\u9886\u57df\u4e8b\u4ef6\u7684\u53d1\u5e03\u5668\u3002 */
    private final MqDomainEventPublisher publisher = new MqDomainEventPublisher();
    /** \u4e8b\u4ef6\u53d1\u5e03\u5668\u5b9e\u9645\u6536\u5230\u7684\u4e8b\u4ef6\uff0c\u4f9b\u540e\u7eed\u65ad\u8a00\u4f7f\u7528\u3002 */
    private final List<MQEvent> publishedEvents = new ArrayList<>();
    /** \u6d4b\u8bd5\u671f\u95f4\u7684\u4ee3\u7406\u53d1\u5e03\u5668\u6ce8\u518c\u8868\u3002 */
    private final Map<String, Consumer<MQEvent>> publisherMap = new ConcurrentHashMap<>();
    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6e05\u9664<code>publisherMap</code>\u3001\u6e05\u9664<code>publishedEvents</code>\u3002 */
    @BeforeEach
    void setUp() {
        publisherMap.clear();
        publishedEvents.clear();
        publisherMap.put("test", event -> publishedEvents.add(event));
        BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publisherMap);
        BaseContext.inject(MQEvent.MQ_PROPERTIES, new MQProperties());
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u79fb\u9664<code>BaseContext</code>\u3001\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void tearDown() {
        BaseContext.remove(MQEvent.MQ_EVENT_PUBLISHER);
        BaseContext.remove(MQEvent.MQ_PROPERTIES);
        ThreadContext.clear();
    }

    // ========================= Scenario: 跨语言提取 =========================

    /** \u9a8c\u8bc1<code>"X-Correlation-Id"</code> \u7b49\u4e8e <code>MQDeliveryHeaders.CORRELATION_ID</code>\uff0c<code>"X-Causation-Id"</code> \u7b49\u4e8e <code>MQDeliveryHeaders.CAUSATION_ID</code>\uff0c<code>brokerHeaders.get(MQDeliveryHeaders.CAUSATION_ID)</code> \u7b49\u4e8e <code>derived.getCausationId().asString()</code>\u3002 */
    @Test
    void carrierMustExposeCausalityAsBrokerHeadersForNonJavaConsumers() {
        RootEvent cause = new RootEvent("order-001");
        DerivedEvent derived = new DerivedEvent("payment-001", cause);

        publisher.publish(derived);

        DomainEventCarrier carrier = (DomainEventCarrier) publishedEvents.get(0);
        Map<String, String> brokerHeaders = carrier.headerMap();
        assertEquals(MQDeliveryHeaders.CORRELATION_ID, "X-Correlation-Id");
        assertEquals(MQDeliveryHeaders.CAUSATION_ID, "X-Causation-Id");
        assertEquals(derived.getCorrelationId().asString(),
                brokerHeaders.get(MQDeliveryHeaders.CORRELATION_ID),
                "非 Java 消费方必须能从 broker header 直接读到 X-Correlation-Id");
        assertEquals(derived.getCausationId().asString(),
                brokerHeaders.get(MQDeliveryHeaders.CAUSATION_ID),
                "非 Java 消费方必须能从 broker header 直接读到 X-Causation-Id");
    }

    // ========================= Scenario: 既有消费方兼容 =========================

    /**
     * \u9a8c\u8bc1<code>payload.get("correlation-id")</code>\u975e\u7a7a\uff0c<code>payload.get("causation-id")</code>\u975e\u7a7a\uff0c<code>payload.get("causation-id").asText()</code> \u7b49\u4e8e <code>derived.getCausationId().asString()</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void payloadMustKeepEmbeddedCausalityForLegacyConsumers() throws Exception {
        RootEvent cause = new RootEvent("order-001");
        DerivedEvent derived = new DerivedEvent("payment-001", cause);

        publisher.publish(derived);

        DomainEventCarrier carrier = (DomainEventCarrier) publishedEvents.get(0);
        JsonNode payload = objectMapper.readTree(carrier.getPayload());
        assertNotNull(payload.get("correlation-id"), "payload 内嵌 correlation-id 必须保留（旧消费方兼容）");
        assertNotNull(payload.get("causation-id"), "payload 内嵌 causation-id 必须保留（旧消费方兼容）");
        assertEquals(derived.getCorrelationId().asString(), payload.get("correlation-id").asText());
        assertEquals(derived.getCausationId().asString(), payload.get("causation-id").asText());
    }

    // ========================= Scenario: 双写一致 =========================

    /**
     * \u9a8c\u8bc1<code>brokerHeaders.get(MQDeliveryHeaders.CORRELATION_ID)</code> \u7b49\u4e8e <code>payload.get("correlation-id").asText()</code>\uff0c<code>brokerHeaders.get(MQDeliveryHeaders.CAUSATION_ID)</code> \u7b49\u4e8e <code>payload.get("causation-id").asText()</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void brokerHeaderAndPayloadMirrorMustBeWrittenFromSameSource() throws Exception {
        RootEvent cause = new RootEvent("order-001");
        DerivedEvent derived = new DerivedEvent("payment-001", cause);

        publisher.publish(derived);

        DomainEventCarrier carrier = (DomainEventCarrier) publishedEvents.get(0);
        Map<String, String> brokerHeaders = carrier.headerMap();
        JsonNode payload = objectMapper.readTree(carrier.getPayload());
        assertEquals(payload.get("correlation-id").asText(),
                brokerHeaders.get(MQDeliveryHeaders.CORRELATION_ID),
                "header 权威值与 payload 镜像值必须同一次写入生成且一致");
        assertEquals(payload.get("causation-id").asText(),
                brokerHeaders.get(MQDeliveryHeaders.CAUSATION_ID),
                "header 权威值与 payload 镜像值必须同一次写入生成且一致");
    }

    // ========================= 无因果源时不写因果 header =========================

    /** \u9a8c\u8bc1<code>brokerHeaders.containsKey(MQDeliveryHeaders.CORRELATION_ID)</code>\u4e3a\u5047\uff0c<code>brokerHeaders.containsKey(MQDeliveryHeaders.CAUSATION_ID)</code>\u4e3a\u5047\u3002 */
    @Test
    void carrierWithoutCausalityMustOmitCausalHeaders() {
        RootEvent root = new RootEvent("order-001");

        publisher.publish(root);

        DomainEventCarrier carrier = (DomainEventCarrier) publishedEvents.get(0);
        Map<String, String> brokerHeaders = carrier.headerMap();
        assertFalse(brokerHeaders.containsKey(MQDeliveryHeaders.CORRELATION_ID),
                "无因果元数据时不得写入空的 X-Correlation-Id");
        assertFalse(brokerHeaders.containsKey(MQDeliveryHeaders.CAUSATION_ID),
                "无因果元数据时不得写入空的 X-Causation-Id");
    }

    // ========================= 测试辅助类 =========================

    /**
     * 无因果源的根事件。
     */
    static class RootEvent extends DomainEvent<StringEntityId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>RootEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>RootEvent</code> \u7684 <code>String</code> \u503c
         */
        RootEvent(String entityId) {
            super(entityId);
        }
    }

    /**
     * 由前置事件触发的派生事件（respondTo 构造语义）。
     */
    static class DerivedEvent extends DomainEvent<StringEntityId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>DerivedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>DerivedEvent</code> \u7684 <code>String</code> \u503c
         * @param respondTo \u7528\u4e8e\u521d\u59cb\u5316 <code>DerivedEvent</code> \u7684 <code>DomainEvent&lt;?&gt;</code> \u503c
         */
        DerivedEvent(String entityId, DomainEvent<?> respondTo) {
            super(new EntityIdPath(new StringEntityId(entityId)), respondTo);
        }
    }
}
