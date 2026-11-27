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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 通过 <code>mock()</code>、<code>BaseContext.inject()</code>、<code>event.setTopic()</code> 验证 <code>SqsAdapterContract</code> 的运行行为。
 */
class SqsAdapterContractTest {

    /**
     * 通过 <code>MessageAttributeValue.builder()</code> 取得当前测试需要的结果。
     *
     * @param value 当前用例传入的值
     * @return 当前测试使用的结果，取自 <code>MessageAttributeValue.builder().dataType("String").stringValue(value).build()</code>
     */
    private static MessageAttributeValue attribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    /**
     * 构造 <code>MQEventSerialization</code> 作为当前测试的输入或结果。
     *
     * @param payload 传给被测操作的消息负载
     * @return 提供给后续契约断言的 <code>MQEventSerialization</code> 结果
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

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证<code>serializations.get()</code> 等于 <code>1</code>。
     */
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

    /**
     * 验证<code>request.getValue().messageAttributes() .get(MQDeliveryHeaders.MESSAGE_ID).stringValue()</code> 等于 <code>"message-1"</code>，<code>request.getValue().messageAttributes() .get(MQDeliveryHeaders.CORRELATION_ID).stringValue()</code> 等于 <code>"correlation-1"</code>，<code>absent.containsKey(MQDeliveryHeaders.CAUSATION_ID)</code>为假。
     */
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
     * 验证<code>probe.event.getCorrelationId()</code> 等于 <code>"native-correlation"</code>，<code>probe.event.getCausationId()</code> 等于 <code>"native-causation"</code>。
     *
     * @throws Exception 反射访问被测成员时失败
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

        Method handle = SqsMQClient.class.getDeclaredMethod("handleMessage",
                SqsClient.class, Message.class, String.class, MQListener.class);
        handle.setAccessible(true);
        handle.invoke(adapter, client, message, "http://queue", listener);

        assertEquals("native-correlation", probe.event.getCorrelationId());
        assertEquals("native-causation", probe.event.getCausationId());
    }

    /**
     * 验证<code>SqsMQClient.messageId(message)</code> 等于 <code>"stable-id"</code>，<code>acknowledgment.messageId()</code> 等于 <code>"stable-id"</code>，<code>acknowledgment.isAcknowledged()</code>为真。
     */
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

    /**
     * 验证<code>acknowledgment.nack(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.reject(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.isAcknowledged()</code>为真。
     */
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

    /**
     * 在测试框架中执行 <code>mock()</code>、<code>adapter.close()</code>、<code>never()</code>，验证该场景。
     */
    @Test
    void shouldNotCloseInjectedClient() {
        SqsClient client = mock(SqsClient.class);
        SqsMQClient adapter = new SqsMQClient(client);

        adapter.close();
        adapter.close();

        verify(client, never()).close();
    }

    /**
     * 将 <code>event</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static class Probe {
        /**
         * 用作输入或用于比较实际发布行为的测试事件。
         */
        private MQEvent event;

        /**
         * 设置<code>event</code> 字段。
         *
         * @param event 要写入的值
         */
        public void onEvent(MQEvent event) {
            this.event = event;
        }
    }
}
