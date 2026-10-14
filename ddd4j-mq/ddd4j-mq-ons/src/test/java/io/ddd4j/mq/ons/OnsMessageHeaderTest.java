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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;

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

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class OnsMessageHeaderTest {

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

    @Test
    void shouldNotShutdownInjectedProducer() {
        Producer producer = mock(Producer.class);
        OnsMQClient client = new OnsMQClient(producer, new OnsProperties());
        client.initProducer(new io.ddd4j.mq.MQProperties());
        client.close();
        client.close();
        verify(producer, never()).shutdown();
    }

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
