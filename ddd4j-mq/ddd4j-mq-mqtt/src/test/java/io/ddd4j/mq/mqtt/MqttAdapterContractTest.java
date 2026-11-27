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
package io.ddd4j.mq.mqtt;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>MqttAdapterContract</code> 在 <code>shouldRejectUnsupportedOperations</code>、<code>shouldLeaveQosMessageUnacknowledged</code> 等场景中的行为。
 */
class MqttAdapterContractTest {

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证<code>acknowledgment.nack(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.isAcknowledged()</code>为假，<code>acknowledgment.isAcknowledged()</code>为真。
     */
    @Test
    void shouldRejectUnsupportedOperationsWithoutAcknowledgingAndAllowAck() {
        MqttMessage message = new MqttMessage("body".getBytes());
        MqttAcknowledgment acknowledgment = new MqttAcknowledgment(message, "orders/paid");

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(true));
        assertFalse(acknowledgment.isAcknowledged(), "nack must leave the message unacknowledged");
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.reject(true));
        assertFalse(acknowledgment.isAcknowledged(), "reject must leave the message unacknowledged");
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.recover(true));
        assertFalse(acknowledgment.isAcknowledged(), "recover must leave the message unacknowledged");

        acknowledgment.ack();
        assertTrue(acknowledgment.isAcknowledged());
    }

    /**
     * 在测试框架中执行 <code>mock()</code>、<code>properties.setEnabled()</code>、<code>properties.setBroker()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void shouldLeaveQosMessageUnacknowledgedWhenHandlerFails() throws Exception {
        MqttClient mqttClient = mock(MqttClient.class);
        MqttMQClient client = new MqttMQClient(mqttClient);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("mqtt");
        FailingHandler handler = new FailingHandler();
        Method method = FailingHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(handler, method, method.getAnnotation(MQEventListener.class));
        client.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
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
        }, null);
        ArgumentCaptor<MqttCallback> callback = forClass(MqttCallback.class);
        verify(mqttClient).setCallback(callback.capture());
        MqttMessage message = new MqttMessage("{}".getBytes());
        message.setQos(1);

        callback.getValue().messageArrived("orders/paid", message);

        verify(mqttClient).setManualAcks(true);
        verify(mqttClient, never()).messageArrivedComplete(anyInt(), anyInt());

        client.close();
        client.close();
        verify(mqttClient).unsubscribe("orders");
        verify(mqttClient, never()).disconnect();
        verify(mqttClient, never()).close();
    }

    /**
     * 提供 <code>handle()</code> 等供当前测试使用的独立操作。
     */
    public static final class FailingHandler {
        /**
         * 构造 <code>IllegalStateException</code> 作为当前测试的输入或结果。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            throw new IllegalStateException("handler failed");
        }
    }
}
