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
package io.ddd4j.mq.ons;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.Producer;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.message.MessageHeaders;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>OnsMessageHeader</code> \u5728 <code>acknowledgment</code>\u3001<code>nativeProperties</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class OnsMessageHeaderTest {

    /** \u9a8c\u8bc1<code>OnsMQClient.messageId(stableMessage)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>OnsMQClient.messageId(legacyMessage)</code> \u7b49\u4e8e <code>"legacy-id"</code>\u3002 */
    @Test
    void shouldPreferStableHeaderAndReadLegacyHeader() {
        Message stableMessage = new Message();
        stableMessage.putUserProperties(MessageHeaders.HEADER_MESSAGE_ID, "stable-id");
        stableMessage.putUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("stable-id", OnsMQClient.messageId(stableMessage));

        Message legacyMessage = new Message();
        legacyMessage.putUserProperties(MessageHeaders.LEGACY_HEADER_MESSAGE_ID, "legacy-id");
        assertEquals("legacy-id", OnsMQClient.messageId(legacyMessage));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>client.initProducer()</code>\u3001<code>client.close()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldNotShutdownInjectedProducer() {
        Producer producer = mock(Producer.class);
        OnsMQClient client = new OnsMQClient(producer, new OnsProperties());
        client.initProducer(new io.ddd4j.mq.MQProperties());
        client.close();
        client.close();
        verify(producer, never()).shutdown();
    }

    /** \u9a8c\u8bc1<code>sent.getValue().getUserProperties(MQDeliveryHeaders.MESSAGE_ID)</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>sent.getValue().getUserProperties(MQDeliveryHeaders.CORRELATION_ID)</code> \u7b49\u4e8e <code>"correlation-id"</code>\uff0c<code>second.getUserProperties(MQDeliveryHeaders.CAUSATION_ID)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void shouldPublishFrozenCausalityAsNativeOnsProperties() {
        Producer producer = mock(Producer.class);
        OnsMQClient client = new OnsMQClient(producer, new OnsProperties());
        BaseContext.inject(MQClient.MQ_SERIALIZATION, serialization());
        try {
            MQEvent event = new MQEvent();
            event.setTopic("orders");
            event.setMsgId("stable-id");
            event.setCorrelationId("correlation-id");
            event.setCausationId("causation-id");
            client.initProducer(new MQProperties()).accept(event);

            ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
            verify(producer).send(sent.capture());
            assertEquals("stable-id", sent.getValue().getUserProperties(MQDeliveryHeaders.MESSAGE_ID));
            assertEquals("correlation-id", sent.getValue().getUserProperties(MQDeliveryHeaders.CORRELATION_ID));
            assertEquals("causation-id", sent.getValue().getUserProperties(MQDeliveryHeaders.CAUSATION_ID));

            MQEvent withoutCausality = new MQEvent();
            withoutCausality.setTopic("orders");
            client.initProducer(new MQProperties()).accept(withoutCausality);
            verify(producer, times(2)).send(sent.capture());
            Message second = sent.getAllValues().get(2);
            assertNull(second.getUserProperties(MQDeliveryHeaders.CORRELATION_ID));
            assertNull(second.getUserProperties(MQDeliveryHeaders.CAUSATION_ID));
        } finally {
            client.close();
            BaseContext.remove(MQClient.MQ_SERIALIZATION);
        }
    }

    /** \u9a8c\u8bc1<code>acknowledgment.messageId()</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>acknowledgment.correlationId()</code> \u7b49\u4e8e <code>"correlation-id"</code>\u3002 */
    @Test
    void acknowledgmentShouldPreferStableIdAndCorrelationHeader() {
        Message nativeMessage = new Message();
        nativeMessage.setMsgID("transport-id");
        nativeMessage.setKey("legacy-key");
        nativeMessage.putUserProperties(MQDeliveryHeaders.MESSAGE_ID, "stable-id");
        nativeMessage.putUserProperties(MQDeliveryHeaders.CORRELATION_ID, "correlation-id");

        OnsAcknowledgment acknowledgment = new OnsAcknowledgment(null, nativeMessage);

        assertEquals("stable-id", acknowledgment.messageId());
        assertEquals("correlation-id", acknowledgment.correlationId());
    }

    /** \u9a8c\u8bc1<code>payload.getMsgId()</code> \u7b49\u4e8e <code>"native-id"</code>\uff0c<code>payload.getCorrelationId()</code> \u7b49\u4e8e <code>"native-correlation"</code>\uff0c<code>legacyPayload.getCorrelationId()</code> \u7b49\u4e8e <code>"payload-correlation"</code>\u3002 */
    @Test
    void nativePropertiesShouldOverridePayloadMirrorBeforeDispatch() {
        Message nativeMessage = new Message();
        nativeMessage.putUserProperties(MQDeliveryHeaders.MESSAGE_ID, "native-id");
        nativeMessage.putUserProperties(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        nativeMessage.putUserProperties(MQDeliveryHeaders.CAUSATION_ID, "native-causation");
        nativeMessage.putUserProperties(MessageHeaders.HEADER_TENANT_ID, "native-tenant");
        MQEvent payload = new MQEvent();
        payload.setMsgId("payload-id");
        payload.setCorrelationId("payload-correlation");
        payload.setCausationId("payload-causation");
        payload.setTenantId("payload-tenant");

        OnsMQClient.applyNativeHeaders(nativeMessage, payload);

        assertEquals("native-id", payload.getMsgId());
        assertEquals("native-correlation", payload.getCorrelationId());
        assertEquals("native-causation", payload.getCausationId());
        assertEquals("native-tenant", payload.getTenantId());

        MQEvent legacyPayload = new MQEvent();
        legacyPayload.setCorrelationId("payload-correlation");
        OnsMQClient.applyNativeHeaders(new Message(), legacyPayload);
        assertEquals("payload-correlation", legacyPayload.getCorrelationId());
    }

    /**
     * \u6784\u9020 <code>MQEventSerialization</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MQEventSerialization</code> \u7ed3\u679c
     */
    private static MQEventSerialization serialization() {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <S, T> T deserialize(S src, Class<T> dist) {
                return (T) new MQEvent();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object src) {
                return (T) "{}";
            }
        };
    }
}
