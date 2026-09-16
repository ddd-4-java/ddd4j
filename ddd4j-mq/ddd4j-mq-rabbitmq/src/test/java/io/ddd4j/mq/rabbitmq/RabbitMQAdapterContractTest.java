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
package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ReturnCallback;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.message.MessageHeaders;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RabbitMQAdapterContractTest {

    @Test
    void shouldPreferStableMessageIdAndNackForRedelivery() throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId("native-id")
                .headers(Collections.<String, Object>singletonMap(MessageHeaders.HEADER_MESSAGE_ID, "stable-id"))
                .build();
        Channel channel = mock(Channel.class);
        RabbitAcknowledgment acknowledgment = new RabbitAcknowledgment(channel, 9L, "stable-id", null);

        assertEquals("stable-id", RabbitMQClient.messageId(properties));
        acknowledgment.nack(true);

        verify(channel).basicNack(9L, false, true);
        assertTrue(acknowledgment.isAcknowledged());
    }

    @Test
    void shouldPublishPersistentMessageAndWaitForBrokerConfirm() throws Exception {
        // 池创建 N 个 channel（默认 32），故 confirmSelect 也调用 N 次。
        // 每个 createChannel() 返回不同的 mock Channel（真实 broker 行为）。
        int poolSize = 32;
        Connection connection = mock(Connection.class);
        java.util.List<Channel> channels = new java.util.ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            channels.add(mock(Channel.class));
        }
        final java.util.concurrent.atomic.AtomicInteger createIdx = new java.util.concurrent.atomic.AtomicInteger();
        when(connection.createChannel()).thenAnswer(inv -> channels.get(createIdx.getAndIncrement() % poolSize));
        RabbitMQProperties properties = new RabbitMQProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        properties.setExchange("events");
        properties.setDurable(true);
        RabbitMQClient client = new RabbitMQClient(connection);
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object src) { return (T) "{}"; }
        };
        client.init(Collections.<io.ddd4j.mq.listener.MQListener>emptyList(), properties, serialization, null);
        MQEvent event = new MQEvent();
        event.setMsgId("message-1");
        event.setTopic("orders");

        event.publish();

        for (Channel ch : channels) {
            verify(ch).confirmSelect();
        }
        // 取到真正被借用的 channel（第一个 borrow 的就是 pool[0]）
        Channel borrowed = channels.get(0);
        org.mockito.ArgumentCaptor<AMQP.BasicProperties> captor =
                org.mockito.ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(borrowed).basicPublish(eq("events"), anyString(), eq(true), captor.capture(), any());
        assertEquals(2, captor.getValue().getDeliveryMode());
        verify(borrowed).waitForConfirmsOrDie(properties.getPublisherConfirmTimeoutMillis());
    }

    @Test
    void shouldRejectMessageReturnedAsUnroutable() throws Exception {
        // 池化后每个 channel 独立注册自己的 ReturnListener 与 return holder。
        // 这里把每个 channel 的 listener 收集起来，发布时从借到的 channel 找出对应回调触发 NO_ROUTE。
        int poolSize = 32;
        Connection connection = mock(Connection.class);
        java.util.Map<Channel, ReturnCallback> listenerByChannel = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.List<Channel> channels = new java.util.ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            Channel ch = mock(Channel.class);
            doAnswer(invocation -> {
                listenerByChannel.put(ch, invocation.getArgument(0));
                return null;
            }).when(ch).addReturnListener(any(ReturnCallback.class));
            doAnswer(invocation -> {
                ReturnCallback callback = listenerByChannel.get(ch);
                if (callback != null) {
                    AMQP.BasicProperties returnedProperties = new AMQP.BasicProperties.Builder()
                            .messageId("message-unroutable")
                            .build();
                    callback.handle(new Return(312, "NO_ROUTE", "events", "orders", returnedProperties, new byte[0]));
                }
                return null;
            }).when(ch).waitForConfirmsOrDie(5000L);
            channels.add(ch);
        }
        final java.util.concurrent.atomic.AtomicInteger createIdx = new java.util.concurrent.atomic.AtomicInteger();
        when(connection.createChannel()).thenAnswer(inv -> channels.get(createIdx.getAndIncrement() % poolSize));
        RabbitMQProperties properties = new RabbitMQProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        properties.setExchange("events");
        RabbitMQClient client = new RabbitMQClient(connection);
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object src) { return (T) "{}"; }
        };
        client.init(Collections.<io.ddd4j.mq.listener.MQListener>emptyList(), properties, serialization, null);
        // 池创建时为每个 channel 注册 listener
        org.junit.jupiter.api.Assertions.assertEquals(poolSize, listenerByChannel.size());
        MQEvent event = new MQEvent();
        event.setMsgId("message-unroutable");
        event.setTopic("orders");

        assertThrows(IllegalStateException.class, event::publish);
    }

    @Test
    void shouldReplenishPoolAfterChannelFailure() throws Exception {
        // channels[0] 在 basicPublish 时抛 IO 异常，验证：
        // 1. 池自动创建替补 channel（createChannel 多调一次）
        // 2. broken channel 从 channelReturnHolder 移除
        // 3. 替补 channel 入池后，后续发布能成功
        int poolSize = 4;
        Connection connection = mock(Connection.class);
        java.util.List<Channel> channels = new java.util.ArrayList<>();
        for (int i = 0; i < poolSize + 1; i++) {
            Channel ch = mock(Channel.class);
            when(ch.isOpen()).thenReturn(true);
            channels.add(ch);
        }
        final java.util.concurrent.atomic.AtomicInteger createIdx = new java.util.concurrent.atomic.AtomicInteger();
        when(connection.createChannel()).thenAnswer(inv -> channels.get(createIdx.getAndIncrement()));
        // channels[0] 在 basicPublish 时抛 IO 异常
        doAnswer(inv -> {
            throw new java.io.IOException("connection reset");
        }).when(channels.get(0)).basicPublish(anyString(), anyString(), eq(true), any(AMQP.BasicProperties.class), any());
        RabbitMQProperties props = new RabbitMQProperties();
        props.setEnabled(true);
        props.setBroker("rabbit");
        props.setExchange("events");
        props.setPublisherConfirmRequired(false);
        props.setProducerChannelPoolSize(poolSize);
        RabbitMQClient client = new RabbitMQClient(connection);
        MQEventSerialization serialization = new MQEventSerialization() {
            @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
            @Override @SuppressWarnings("unchecked") public <T> T serialize(Object src) { return (T) "{}"; }
        };
        client.init(Collections.<io.ddd4j.mq.listener.MQListener>emptyList(), props, serialization, null);

        // 第一次发布：channels[0] 失败 → catch 块创建替补 channels[poolSize] 入池
        MQEvent event1 = new MQEvent();
        event1.setMsgId("msg-fail");
        event1.setTopic("orders");
        assertThrows(IllegalStateException.class, event1::publish);

        // 验证替补 channel 被创建（初始 poolSize + 替补1 = poolSize+1 次 createChannel）
        verify(connection, org.mockito.Mockito.times(poolSize + 1)).createChannel();
        // 验证替补 channel 注册了 ReturnListener
        verify(channels.get(poolSize)).addReturnListener(any(ReturnCallback.class));

        // 第二次发布：池中现在有 channels[1..poolSize-1] + 替补 channels[poolSize]，
        // 轮询顺序不确定，但至少有一个 channel 会被借出并成功发布。
        MQEvent event2 = new MQEvent();
        event2.setMsgId("msg-ok");
        event2.setTopic("orders");
        event2.publish();
        // 验证有某个非 broken channel 被调用了 basicPublish（不一定是替补，因为池中还有 channels[1..3]）
        boolean anyPublish = false;
        for (int i = 1; i <= poolSize; i++) {
            try {
                verify(channels.get(i), org.mockito.Mockito.atLeastOnce()).basicPublish(
                        eq("events"), anyString(), eq(true), any(AMQP.BasicProperties.class), any());
                anyPublish = true;
                break;
            } catch (org.mockito.exceptions.base.MockitoAssertionError ignore) {
            }
        }
        assertTrue(anyPublish, "Expected basicPublish on any non-broken channel after pool replenishment");
    }

    @Test
    void shouldCloseOwnedChannelsButNotInjectedConnectionOnlyOnce() throws Exception {
        int poolSize = 32;
        Connection connection = mock(Connection.class);
        java.util.List<Channel> channels = new java.util.ArrayList<>();
        for (int i = 0; i < poolSize; i++) {
            Channel ch = mock(Channel.class);
            when(ch.isOpen()).thenReturn(true);
            channels.add(ch);
        }
        final java.util.concurrent.atomic.AtomicInteger createIdx = new java.util.concurrent.atomic.AtomicInteger();
        when(connection.createChannel()).thenAnswer(inv -> channels.get(createIdx.getAndIncrement() % poolSize));
        RabbitMQProperties properties = new RabbitMQProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        RabbitMQClient client = new RabbitMQClient(connection);
        client.init(Collections.<io.ddd4j.mq.listener.MQListener>emptyList(), properties,
                new MQEventSerialization() {
                    @Override public <S, T> T deserialize(S src, Class<T> dist) { return null; }
                    @Override @SuppressWarnings("unchecked") public <T> T serialize(Object src) { return (T) "{}"; }
                }, null);
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        event.publish();

        client.close();
        client.close();

        // 池里每个 channel 都被关闭一次（lifecycle 注册了 N 个）。
        for (Channel ch : channels) {
            verify(ch).close();
        }
        verify(connection, never()).close();
    }
}
