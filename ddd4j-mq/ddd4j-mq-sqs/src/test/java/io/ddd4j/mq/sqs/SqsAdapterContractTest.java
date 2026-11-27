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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** \u901a\u8fc7 <code>mock()</code>\u3001<code>BaseContext.inject()</code>\u3001<code>event.setTopic()</code> \u9a8c\u8bc1 <code>SqsAdapterContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class SqsAdapterContractTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>serializations.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void successfulNativeSendDoesNotSerializeAgainForLogging() {
        SqsClient client = mock(SqsClient.class);
        SqsMQClient adapter = new SqsMQClient(client);
        AtomicInteger serializations = new AtomicInteger();
        BaseContext.inject(MQClient.MQ_SERIALIZATION, new MQEventSerialization() {
            @Override
            public <S, T> T deserialize(S src, Class<T> dist) {
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object src) {
                if (serializations.incrementAndGet() > 1) {
                    throw new IllegalStateException("serialized twice after native send");
                }
                return (T) "{}";
            }
        });
        MQEvent event = new MQEvent();
        event.setTopic("http://queue");

        try {
            adapter.initProducer(new SqsProperties()).accept(event);
            verify(client, times(1)).sendMessage(any(SendMessageRequest.class));
            assertEquals(1, serializations.get());
        } finally {
            adapter.close();
        }
    }

    /** \u9a8c\u8bc1<code>request.getValue().messageAttributes() .get(MQDeliveryHeaders.MESSAGE_ID).stringValue()</code> \u7b49\u4e8e <code>"message-1"</code>\uff0c<code>request.getValue().messageAttributes() .get(MQDeliveryHeaders.CORRELATION_ID).stringValue()</code> \u7b49\u4e8e <code>"correlation-1"</code>\uff0c<code>absent.containsKey(MQDeliveryHeaders.CAUSATION_ID)</code>\u4e3a\u5047\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>probe.event.getCorrelationId()</code> \u7b49\u4e8e <code>"native-correlation"</code>\uff0c<code>probe.event.getCausationId()</code> \u7b49\u4e8e <code>"native-causation"</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put(MQDeliveryHeaders.CORRELATION_ID, attribute("native-correlation"));
        attributes.put(MQDeliveryHeaders.CAUSATION_ID, attribute("native-causation"));
        Message message = Message.builder().receiptHandle("receipt")
                .body("{}").messageAttributes(attributes).build();

        Method handle = SqsMQClient.class.getDeclaredMethod("handleMessage", Message.class, String.class, MQListener.class);
        handle.setAccessible(true);
        handle.invoke(adapter, message, "http://queue", listener);

        assertEquals("native-correlation", probe.event.getCorrelationId());
        assertEquals("native-causation", probe.event.getCausationId());
    }

    /**
     * \u901a\u8fc7 <code>MessageAttributeValue.builder()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>MessageAttributeValue.builder().dataType("String").stringValue(value).build()</code>
     */
    private static MessageAttributeValue attribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    /**
     * \u6784\u9020 <code>MQEventSerialization</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param payload \u4f20\u7ed9\u88ab\u6d4b\u64cd\u4f5c\u7684\u6d88\u606f\u8d1f\u8f7d
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQEventSerialization</code> \u7ed3\u679c
     */
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

    /** \u5c06 <code>event</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static class Probe {
        /** \u7528\u4f5c\u8f93\u5165\u6216\u7528\u4e8e\u6bd4\u8f83\u5b9e\u9645\u53d1\u5e03\u884c\u4e3a\u7684\u6d4b\u8bd5\u4e8b\u4ef6\u3002 */
        private MQEvent event;

        /**
         * \u8bbe\u7f6e<code>event</code> \u5b57\u6bb5\u3002
         * @param event \u8981\u5199\u5165\u7684\u503c
         */
        public void onEvent(MQEvent event) {
            this.event = event;
        }
    }

    /** \u9a8c\u8bc1<code>SqsMQClient.messageId(message)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>acknowledgment.messageId()</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
    @Test
    void shouldPreferStableMessageIdAndResetVisibilityForRedelivery() {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put(MessageHeaders.HEADER_MESSAGE_ID, attribute("stable-id"));
        attributes.put(MQDeliveryHeaders.CORRELATION_ID, attribute("correlation-1"));
        Message message = Message.builder()
                .messageId("transport-id")
                .receiptHandle("receipt")
                .messageAttributes(attributes)
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

    /** \u9a8c\u8bc1<code>acknowledgment.nack(true)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>acknowledgment.reject(true)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\u3002 */
    @Test
    void disabledRequeueDoesNotDeleteRequestedRedelivery() {
        Message message = Message.builder().messageId("transport-id").receiptHandle("receipt").build();
        SqsClient client = mock(SqsClient.class);
        SqsAcknowledgment acknowledgment = new SqsAcknowledgment(client, message, "http://queue", false);

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.reject(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.recover(true));
        assertFalse(acknowledgment.isAcknowledged());
        verifyNoInteractions(client);

        acknowledgment.nack(false);

        verify(client).deleteMessage(any(DeleteMessageRequest.class));
        assertTrue(acknowledgment.isAcknowledged());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>adapter.close()</code>\u3001<code>never()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldNotCloseInjectedClient() {
        SqsClient client = mock(SqsClient.class);
        SqsMQClient adapter = new SqsMQClient(client);

        adapter.close();
        adapter.close();

        verify(client, never()).close();
    }
}
