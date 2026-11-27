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
package io.ddd4j.mq.kafka;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kafka 生产端因果 broker header 写入行为测试。
 *
 * <p>对应 OpenSpec change {@code promote-causality-to-broker-headers}，
 * 派生自家族规格 Requirement: Causality in broker headers 的跨语言提取场景：
 * 非 Java 消费方从 Kafka 消息 headers 直接读到 {@code X-Correlation-Id}/{@code X-Causation-Id}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class KafkaCausalityBrokerHeaderMustTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>correlationHeader</code>\u975e\u7a7a\uff0c<code>causationHeader</code>\u975e\u7a7a\u3002 */
    @Test
    @SuppressWarnings("unchecked")
    void kafkaRecordMustCarryCausalityHeadersForNonJavaConsumers() {
        Producer<String, String> producer = mock(Producer.class);
        CompletableFuture<RecordMetadata> acked = new CompletableFuture<>();
        acked.complete(null);
        when(producer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(acked);
        KafkaMQClient client = new KafkaMQClient(producer, null);
        client.init(Collections.<MQListener>emptyList(), properties(), serialization(), null);

        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setCorrelationId("11111111-1111-1111-1111-111111111111");
        event.setCausationId("22222222-2222-2222-2222-222222222222");

        event.publish();

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass((Class<ProducerRecord<String, String>>) (Class<?>) ProducerRecord.class);
        verify(producer).send(captor.capture(), any(Callback.class));
        Header correlationHeader = captor.getValue().headers().lastHeader(MQDeliveryHeaders.CORRELATION_ID);
        Header causationHeader = captor.getValue().headers().lastHeader(MQDeliveryHeaders.CAUSATION_ID);
        assertNotNull(correlationHeader, "Kafka 消息必须携带 X-Correlation-Id broker header");
        assertNotNull(causationHeader, "Kafka 消息必须携带 X-Causation-Id broker header");
        assertEquals("11111111-1111-1111-1111-111111111111",
                new String(correlationHeader.value(), StandardCharsets.UTF_8));
        assertEquals("22222222-2222-2222-2222-222222222222",
                new String(causationHeader.value(), StandardCharsets.UTF_8));
    }

    /**
     * \u8bfb\u53d6 <code>properties</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>properties</code>
     */
    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        properties.setAutoAck(false);
        return properties;
    }

    /**
     * \u6784\u9020 <code>MQEventSerialization</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQEventSerialization</code> \u7ed3\u679c
     */
    private MQEventSerialization serialization() {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                MQEvent event = new MQEvent();
                event.setTopic("orders");
                return type.cast(event);
            }
        };
    }

    /**
     * 供 MQListener.of 反射使用的占位监听器（对齐既有契约测试写法）。
     */
    public static final class OrdersHandler {
        /**
         * \u6ce8\u518c\u6d4b\u8bd5\u7528\u7684\u8ba2\u5355\u4e3b\u9898\u76d1\u542c\u5668\uff0c\u6d88\u8d39\u65f6\u4e0d\u6539\u53d8\u65ad\u8a00\u72b6\u6001\u3002
         * @param event \u7531\u805a\u5408\u6216\u76d1\u542c\u5668\u6536\u5230\u7684\u6d4b\u8bd5\u4e8b\u4ef6
         */
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            // 测试占位：不参与断言
        }
    }
}
