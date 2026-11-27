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

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

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

    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("kafka");
        properties.setAutoAck(false);
        return properties;
    }

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
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            // 测试占位：不参与断言
        }
    }
}
