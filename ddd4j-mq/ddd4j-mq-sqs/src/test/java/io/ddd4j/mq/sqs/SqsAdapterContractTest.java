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
package io.ddd4j.mq.sqs;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SqsAdapterContractTest {

    @Test
    void shouldPublishFrozenCausalityHeadersAsNativeAttributes() {
        SqsClient client = mock(SqsClient.class);
        SqsMQClient adapter = new SqsMQClient(client);
        BaseContext.inject(MQClient.MQ_SERIALIZATION, serialization(new MQEvent()));
        MQEvent event = new MQEvent();
        event.setTopic("http://queue");
        event.setMsgId("message-1");
        event.setCorrelationId("correlation-1");
        event.setCausationId("causation-1");

        adapter.initProducer(new SqsProperties()).accept(event);

        org.mockito.ArgumentCaptor<SendMessageRequest> request = forClass(SendMessageRequest.class);
        verify(client).sendMessage(request.capture());
        assertEquals("message-1", request.getValue().messageAttributes()
                .get(MQDeliveryHeaders.MESSAGE_ID).stringValue());
        assertEquals("correlation-1", request.getValue().messageAttributes()
                .get(MQDeliveryHeaders.CORRELATION_ID).stringValue());
        assertEquals("causation-1", request.getValue().messageAttributes()
                .get(MQDeliveryHeaders.CAUSATION_ID).stringValue());

        MQEvent withoutCausality = new MQEvent();
        withoutCausality.setTopic("http://queue");
        adapter.initProducer(new SqsProperties()).accept(withoutCausality);
        verify(client, times(2)).sendMessage(request.capture());
        Map<String, MessageAttributeValue> absent = request.getAllValues().get(2).messageAttributes();
        assertFalse(absent.containsKey(MQDeliveryHeaders.CORRELATION_ID));
        assertFalse(absent.containsKey(MQDeliveryHeaders.CAUSATION_ID));
    }

    @Test
    void shouldRestoreNativeCausalityBeforeInvokingListener() throws Exception {
        SqsClient client = mock(SqsClient.class);
        SqsMQClient adapter = new SqsMQClient(client);
        MQEvent payload = new MQEvent();
        payload.setCorrelationId("payload-correlation");
        payload.setCausationId("payload-causation");
        BaseContext.inject(MQClient.MQ_SERIALIZATION, serialization(payload));
        Probe probe = new Probe();
        MQListener listener = new MQListener();
        listener.setBean(probe);
        listener.setMethod(Probe.class.getMethod("onEvent", MQEvent.class));
        listener.setSupports(Arrays.asList("*"));
        Message message = Message.builder().receiptHandle("receipt")
                .body("{}")
                .messageAttributes(Map.of(
                        MQDeliveryHeaders.CORRELATION_ID, attribute("native-correlation"),
                        MQDeliveryHeaders.CAUSATION_ID, attribute("native-causation")))
                .build();

        Method handle = SqsMQClient.class.getDeclaredMethod("handleMessage", Message.class, String.class, MQListener.class);
        handle.setAccessible(true);
        handle.invoke(adapter, message, "http://queue", listener);

        assertEquals("native-correlation", probe.event.getCorrelationId());
        assertEquals("native-causation", probe.event.getCausationId());
    }

    private static MessageAttributeValue attribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private static MQEventSerialization serialization(MQEvent payload) {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <S, T> T deserialize(S src, Class<T> dist) {
                return (T) payload;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object src) {
                return (T) "{}";
            }
        };
    }

    public static class Probe {
        private MQEvent event;

        public void onEvent(MQEvent event) {
            this.event = event;
        }
    }

    @Test
    void shouldPreferStableMessageIdAndResetVisibilityForRedelivery() {
        Message message = Message.builder()
                .messageId("transport-id")
                .receiptHandle("receipt")
                .messageAttributes(Map.of(
                        MessageHeaders.HEADER_MESSAGE_ID, attribute("stable-id"),
                        MQDeliveryHeaders.CORRELATION_ID, attribute("correlation-1")))
                .build();
        SqsClient client = mock(SqsClient.class);
        SqsAcknowledgment acknowledgment = new SqsAcknowledgment(client, message, "http://queue", true);

        assertEquals("stable-id", SqsMQClient.messageId(message));
        assertEquals("stable-id", acknowledgment.messageId());
        assertEquals("correlation-1", acknowledgment.correlationId());
        acknowledgment.nack(true);

        verify(client).changeMessageVisibility(any(java.util.function.Consumer.class));
        assertTrue(acknowledgment.isAcknowledged());
    }

    @Test
    void shouldNotCloseInjectedClient() {
        SqsClient client = mock(SqsClient.class);
        SqsMQClient adapter = new SqsMQClient(client);

        adapter.close();
        adapter.close();

        verify(client, never()).close();
    }
}
