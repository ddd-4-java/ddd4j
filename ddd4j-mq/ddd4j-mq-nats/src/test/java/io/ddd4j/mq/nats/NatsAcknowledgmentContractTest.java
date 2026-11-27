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
package io.ddd4j.mq.nats;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>NatsAcknowledgment</code> 在<code>nativeHeaders</code>等操作中的行为契约。
 */
class NatsAcknowledgmentContractTest {

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证<code>acknowledgment.isAcknowledged()</code>为真。
     */
    @Test
    void shouldMapNackToJetStreamNakForRedelivery() {
        Message message = mock(Message.class);
        NatsAcknowledgment acknowledgment = new NatsAcknowledgment(message);

        acknowledgment.nack(true);

        verify(message).nak();
        assertTrue(acknowledgment.isAcknowledged());
    }

    /**
     * 在测试框架中执行 <code>mock()</code>、<code>headers.put()</code>、<code>message.getHeaders()</code>，验证该场景。
     */
    @Test
    void shouldPreferNativeCorrelationHeaderInAcknowledgment() {
        Message message = mock(Message.class);
        Headers headers = new Headers();
        headers.put(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        when(message.getHeaders()).thenReturn(headers);
        when(message.getReplyTo()).thenReturn("legacy-reply");

        assertEquals("native-correlation", new NatsAcknowledgment(message).correlationId());

        when(message.getHeaders()).thenReturn(null);
        assertEquals("legacy-reply", new NatsAcknowledgment(message).correlationId());
    }

    /**
     * 验证<code>handler.received</code> 与…为同一实例 <code>event</code>，<code>handler.received.getCorrelationId()</code> 等于 <code>"native-correlation"</code>，<code>handler.received.getTag()</code> 等于 <code>"priority"</code>。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void nativeHeadersShouldOverridePayloadBeforeListener() throws Exception {
        Connection connection = mock(Connection.class);
        NatsMQClient client = new NatsMQClient(connection);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("nats");
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.setCorrelationId("payload-correlation");
        event.setCausationId("payload-causation");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object value) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return type.cast(event);
            }
        }, null);
        CapturingHandler handler = new CapturingHandler();
        Method handlerMethod = CapturingHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(handler, handlerMethod,
                handlerMethod.getAnnotation(MQEventListener.class));
        Message message = mock(Message.class);
        Headers headers = new Headers();
        headers.put(MQDeliveryHeaders.CORRELATION_ID, "native-correlation");
        headers.put(MQDeliveryHeaders.CAUSATION_ID, "native-causation");
        headers.put(io.ddd4j.mq.message.MessageHeaders.HEADER_DESTINATION_TAG, "priority");
        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(message.getSubject()).thenReturn("orders");

        Method onMessage = NatsMQClient.class.getDeclaredMethod("onMessage", Message.class, MQListener.class);
        onMessage.setAccessible(true);
        onMessage.invoke(client, message, listener);

        assertSame(event, handler.received);
        assertEquals("native-correlation", handler.received.getCorrelationId());
        assertEquals("native-causation", handler.received.getCausationId());
        assertEquals("priority", handler.received.getTag());
        client.close();
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldFailInsteadOfFallingBackToNonDurableCorePublish() throws Exception {
        Connection connection = mock(Connection.class);
        JetStream jetStream = mock(JetStream.class);
        when(connection.jetStream()).thenReturn(jetStream);
        doThrow(new IOException("jetstream unavailable"))
                .when(jetStream).publish(anyString(), any(Headers.class), any(byte[].class));
        NatsMQClient client = new NatsMQClient(connection);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("nats");
        client.init(Collections.<MQListener>emptyList(), properties, new MQEventSerialization() {
            @Override
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        }, null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
        verify(connection, never()).publish(anyString(), any(Headers.class), any(byte[].class));

        client.close();
        client.close();
        verify(connection, never()).close();
        verify(connection, never()).drain(any(java.time.Duration.class));
    }

    /**
     * 将 <code>received</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class CapturingHandler {
        /**
         * 当前测试接收的数据，供断言核对投递内容或次数。
         */
        private MQEvent received;

        /**
         * 根据本次调用更新 <code>received = event</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @MQEventListener(topic = "orders", tags = "priority")
        public void handle(MQEvent event) {
            received = event;
        }
    }
}
