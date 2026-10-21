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

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

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
