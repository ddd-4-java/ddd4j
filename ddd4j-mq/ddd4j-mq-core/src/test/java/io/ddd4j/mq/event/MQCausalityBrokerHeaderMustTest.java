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
     * 用于读写 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

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
     * 测试后依次移除<code>BaseContext</code>、清除<code>ThreadContext</code>。
     */
    @AfterEach
    void tearDown() {
        BaseContext.remove(MQEvent.MQ_EVENT_PUBLISHER);
        BaseContext.remove(MQEvent.MQ_PROPERTIES);
        ThreadContext.clear();
    }

    // ========================= Scenario: 跨语言提取 =========================

    /**
     * 验证<code>"X-Correlation-Id"</code> 等于 <code>MQDeliveryHeaders.CORRELATION_ID</code>，<code>"X-Causation-Id"</code> 等于 <code>MQDeliveryHeaders.CAUSATION_ID</code>，<code>brokerHeaders.get(MQDeliveryHeaders.CAUSATION_ID)</code> 等于 <code>derived.getCausationId().asString()</code>。
     */
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
     * 验证<code>payload.get("correlation-id")</code>非空，<code>payload.get("causation-id")</code>非空，<code>payload.get("causation-id").asText()</code> 等于 <code>derived.getCausationId().asString()</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
     * 验证<code>brokerHeaders.get(MQDeliveryHeaders.CORRELATION_ID)</code> 等于 <code>payload.get("correlation-id").asText()</code>，<code>brokerHeaders.get(MQDeliveryHeaders.CAUSATION_ID)</code> 等于 <code>payload.get("causation-id").asText()</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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

    /**
     * 验证<code>brokerHeaders.containsKey(MQDeliveryHeaders.CORRELATION_ID)</code>为假，<code>brokerHeaders.containsKey(MQDeliveryHeaders.CAUSATION_ID)</code>为假。
     */
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
         * 使用 <code>super()</code> 初始化 <code>RootEvent</code> 测试夹具。
         *
         * @param entityId 用于初始化 <code>RootEvent</code> 的 <code>String</code> 值
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
         * 使用 <code>super()</code> 初始化 <code>DerivedEvent</code> 测试夹具。
         *
         * @param entityId  用于初始化 <code>DerivedEvent</code> 的 <code>String</code> 值
         * @param respondTo 用于初始化 <code>DerivedEvent</code> 的 <code>DomainEvent&lt;?&gt;</code> 值
         */
        DerivedEvent(String entityId, DomainEvent<?> respondTo) {
            super(new EntityIdPath(new StringEntityId(entityId)), respondTo);
        }
    }
}
