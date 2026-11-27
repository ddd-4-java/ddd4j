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
package io.ddd4j.mq.rocketmq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.MessageHeaders;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/**
 * 类型说明。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class RocketMessageHeaderTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>RocketMQClient.messageId(stableMessage)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>RocketMQClient.messageId(legacyMessage)</code> \u7b49\u4e8e <code>"legacy-id"</code>\u3002 */
    @Test
    void shouldPreferStableHeaderAndReadLegacyHeader() {
        MessageExt stableMessage = new MessageExt();
        stableMessage.putUserProperty(MessageHeaders.HEADER_MESSAGE_ID, "stable-id");
        stableMessage.putUserProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("stable-id", RocketMQClient.messageId(stableMessage));

        MessageExt legacyMessage = new MessageExt();
        legacyMessage.putUserProperty(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("legacy-id", RocketMQClient.messageId(legacyMessage));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>client.close()</code>\u3001<code>never()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldNotShutdownExternallyInjectedProducer() {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RocketMQClient client = new RocketMQClient(producer);

        client.close();
        client.close();

        verify(producer, never()).shutdown();
    }

    /**
     * \u9a8c\u8bc1<code>sent.getValue().getTopic()</code> \u7b49\u4e8e <code>"tenant_orders"</code>\uff0c<code>sent.getValue().getTags()</code> \u7b49\u4e8e <code>"paid"</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void shouldPublishToNamespacedTopicWithoutAppendingNativeTag() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RocketMQClient client = new RocketMQClient(producer);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("rocket");
        properties.setNamespace("fallback");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        }, null);

        MQEvent event = new MQEvent();
        event.setNamespace("tenant");
        event.setTopic("orders");
        event.setTag("paid");
        event.publish();

        org.mockito.ArgumentCaptor<Message> sent = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(producer).send(sent.capture(), any(org.apache.rocketmq.client.producer.MessageQueueSelector.class), anyString());
        assertEquals("tenant_orders", sent.getValue().getTopic());
        assertEquals("paid", sent.getValue().getTags());
        client.close();
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>spy()</code>\u3001<code>properties.setNamespace()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldSubscribeToNamespacedTopicWithNativeTagFilter() throws Exception {
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        RocketMQProperties properties = spy(new RocketMQProperties());
        properties.setNamespace("fallback");
        doReturn(consumer).when(properties).newConsumer("group");
        MQListener listener = MQListener.builder().group("group").namespace("tenant")
                .topic("orders").tags("paid").build();
        RocketMQClient client = new RocketMQClient(properties);

        client.initConsumer(listener, properties);

        verify(consumer).subscribe("tenant_orders", "paid");
        client.close();
    }

    /**
     * \u9a8c\u8bc1<code>messages.getValue().getUserProperty(MQDeliveryHeaders.MESSAGE_ID)</code> \u7b49\u4e8e <code>"plain-id"</code>\uff0c<code>messages.getValue().getUserProperty(MQDeliveryHeaders.CORRELATION_ID)</code>\u4e3a\u7a7a\uff0c<code>sent.getUserProperty(MQDeliveryHeaders.CAUSATION_ID)</code> \u7b49\u4e8e <code>"causation-id"</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void shouldPublishOnlyPresentCausalityInNativeProperties() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RocketMQClient client = new RocketMQClient(producer);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("rocket");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        }, null);

        MQEvent plain = new MQEvent();
        plain.setTopic("orders");
        plain.setMsgId("plain-id");
        plain.publish();
        org.mockito.ArgumentCaptor<Message> messages = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(producer).send(messages.capture());
        assertEquals("plain-id", messages.getValue().getUserProperty(MQDeliveryHeaders.MESSAGE_ID));
        assertNull(messages.getValue().getUserProperty(MQDeliveryHeaders.CORRELATION_ID));
        assertNull(messages.getValue().getUserProperty(MQDeliveryHeaders.CAUSATION_ID));
        clearInvocations(producer);

        MQEvent causal = new MQEvent();
        causal.setTopic("orders");
        causal.setMsgId("causal-id");
        causal.setCorrelationId("correlation-id");
        causal.setCausationId("causation-id");
        causal.publish();
        verify(producer).send(messages.capture());
        Message sent = messages.getValue();
        assertEquals("causal-id", sent.getUserProperty(MQDeliveryHeaders.MESSAGE_ID));
        assertEquals("correlation-id", sent.getUserProperty(MQDeliveryHeaders.CORRELATION_ID));
        assertEquals("causation-id", sent.getUserProperty(MQDeliveryHeaders.CAUSATION_ID));
        client.close();
    }

    /** \u9a8c\u8bc1<code>event.getCorrelationId()</code> \u7b49\u4e8e <code>"native-correlation"</code>\uff0c<code>event.getCausationId()</code> \u7b49\u4e8e <code>"native-causation"</code>\u3002 */
    @Test
    void shouldRestoreNativeCausalityOverPayloadMirror() {
        MessageExt message = new MessageExt();
        message.putUserProperty(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        message.putUserProperty(MQDeliveryHeaders.CAUSATION_ID, "native-causation");
        MQEvent event = new MQEvent();
        event.setCorrelationId("payload-correlation");
        event.setCausationId("payload-causation");

        RocketMQClient.restoreCausality(message, event);

        assertEquals("native-correlation", event.getCorrelationId());
        assertEquals("native-causation", event.getCausationId());
    }

    /** \u9a8c\u8bc1<code>event.getCorrelationId()</code> \u7b49\u4e8e <code>"native-correlation"</code>\uff0c<code>event.getCausationId()</code> \u7b49\u4e8e <code>"payload-causation"</code>\u3002 */
    @Test
    void shouldPreservePayloadWhenNativeCausalityIsAbsent() {
        MessageExt message = new MessageExt();
        message.putUserProperty(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        MQEvent event = new MQEvent();
        event.setCorrelationId("payload-correlation");
        event.setCausationId("payload-causation");

        RocketMQClient.restoreCausality(message, event);

        assertEquals("native-correlation", event.getCorrelationId());
        assertEquals("payload-causation", event.getCausationId());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>nativeMessage.setKeys()</code>\u3001<code>nativeMessage.putUserProperty()</code>\u3001<code>legacyMessage.setKeys()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldExposeNativeCorrelationInAcknowledgmentWithLegacyFallback() {
        MessageExt nativeMessage = new MessageExt();
        nativeMessage.setKeys("legacy-key");
        nativeMessage.putUserProperty(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        assertEquals("native-correlation", new RocketAcknowledgment(nativeMessage).correlationId());

        MessageExt legacyMessage = new MessageExt();
        legacyMessage.setKeys("legacy-key");
        assertEquals("legacy-key", new RocketAcknowledgment(legacyMessage).correlationId());
    }
}
