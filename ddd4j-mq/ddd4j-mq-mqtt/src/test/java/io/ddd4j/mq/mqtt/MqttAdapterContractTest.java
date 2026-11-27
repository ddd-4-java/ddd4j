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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;

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

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
class MqttAdapterContractTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /** 验证<code>acknowledgment.nack(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.reject(true)</code> 抛出 <code>UnsupportedOperationException.class</code>，<code>acknowledgment.recover(true)</code> 抛出 <code>UnsupportedOperationException.class</code>。 */
    @Test
    void shouldTreatMessageIdAsPayloadConcernAndRejectNativeNack() {
        MqttMessage message = new MqttMessage("body".getBytes());
        MqttAcknowledgment acknowledgment = new MqttAcknowledgment(message, "orders/paid");

        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.nack(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.reject(true));
        assertThrows(UnsupportedOperationException.class, () -> acknowledgment.recover(true));
        assertFalse(acknowledgment.isAcknowledged());

        acknowledgment.ack();
        assertTrue(acknowledgment.isAcknowledged());
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>properties.setEnabled()</code>\u3001<code>properties.setBroker()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void shouldLeaveQosMessageUnacknowledgedWhenHandlerFails() throws Exception {
        MqttClient mqttClient = mock(MqttClient.class);
        MqttMQClient client = new MqttMQClient(mqttClient);
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        /**
         * properties.setBroker() 方法。
         * @param "mqtt" "mqtt" 参数。
         */
        properties.setBroker("mqtt");
        FailingHandler handler = new FailingHandler();
        Method method = FailingHandler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(handler, method, method.getAnnotation(MQEventListener.class));
        client.init(Collections.singletonList(listener), properties, new MQEventSerialization() {
            /**
             * serialize() 方法。
             * @param <T> 类型参数。
             * @param event 事件消息。
             * @return 结果值。
             */
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            /**
             * deserialize() 方法。
             * @param <S> 类型参数。
             * @param <T> 类型参数。
             * @param value 值。
             * @param type 目标类型。
             * @return 结果值。
             */
            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                MQEvent event = new MQEvent();
                event.setTopic("orders");
                return type.cast(event);
            }
        }, null);
        org.mockito.ArgumentCaptor<MqttCallback> callback = forClass(MqttCallback.class);
        verify(mqttClient).setCallback(callback.capture());
        MqttMessage message = new MqttMessage("{}".getBytes());
        /**
         * message.setQos() 方法。
         * @param 1 1 参数。
         */
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
     * 必定抛出异常的监听方法宿主，用于验证消费失败时不回执消息。
     */
    public static final class FailingHandler {
        /**
         * 处理请求。
         * @param event 事件消息。
         */
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
            throw new IllegalStateException("handler failed");
        }
    }
}
