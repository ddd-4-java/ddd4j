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
package io.ddd4j.mq.disruptor;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lmax.disruptor.RingBuffer;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>DisruptorAdapterContract</code> \u5728 <code>shouldDeliverRawTopicExactlyOnce</code>\u3001<code>shouldDeliverUsingConfiguredNamespace</code>\u3001<code>shouldRetainStableMessageId</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class DisruptorAdapterContractTest {

    /**
     * \u9a8c\u8bc1<code>target.delivered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>queued.getTopic()</code> \u7b49\u4e8e <code>"orders"</code>\uff0c<code>target.received.getMsgId()</code> \u7b49\u4e8e <code>"stable-route-id"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void shouldDeliverRawTopicExactlyOnceWithoutComposingRouteTwice() throws Exception {
        DisruptorMQProperties properties = new DisruptorMQProperties();
        properties.setEnabled(true);
        properties.setBroker("disruptor");
        properties.setBufferSize(8);
        RecordingListener target = new RecordingListener();
        MQListener listener = MQListener.builder()
                .bean(target)
                .method(RecordingListener.class.getMethod("onEvent", MQEvent.class))
                .namespace("production")
                .topic("orders")
                .tags("paid")
                .supports(Collections.singletonList("*"))
                .build();
        MQEvent original = new MQEvent();
        original.setNamespace("production");
        original.setTopic("orders");
        original.setTag("paid");
        original.setMsgId("stable-route-id");
        try (DisruptorMQClient client = new DisruptorMQClient(properties)) {
            client.init(Collections.singletonList(listener), properties, new JsonMQEventSerialization(), null);
            client.initProducer(properties).accept(original);
            DisruptorEvent queued = client.getRingBuffer().get(client.getRingBuffer().getCursor());
            assertTrue(target.delivered.await(5, TimeUnit.SECONDS),
                    "Expected listener route production.orders.paid but queued " + queued.getRouteExpression());
            assertEquals("orders", queued.getTopic());
            assertEquals("production.orders.paid", queued.getRouteExpression());
            assertEquals("stable-route-id", queued.getMessageId());
        } finally {
            BaseContext.clear();
        }
        assertEquals(1, target.calls.get());
        assertNotSame(original, target.received);
        assertEquals("production", target.received.getNamespace());
        assertEquals("orders", target.received.getTopic());
        assertEquals("paid", target.received.getTag());
        assertEquals("stable-route-id", target.received.getMsgId());
    }

    /**
     * \u9a8c\u8bc1<code>target.delivered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>queued.getNamespace()</code> \u7b49\u4e8e <code>"production"</code>\uff0c<code>target.received.getMsgId()</code> \u7b49\u4e8e <code>"configured-namespace-id"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void shouldDeliverUsingConfiguredNamespaceWhenEventNamespaceIsAbsent() throws Exception {
        DisruptorMQProperties properties = new DisruptorMQProperties();
        properties.setEnabled(true);
        properties.setBroker("disruptor");
        properties.setNamespace("production");
        properties.setBufferSize(8);
        RecordingListener target = new RecordingListener();
        MQListener listener = MQListener.builder()
                .bean(target)
                .method(RecordingListener.class.getMethod("onEvent", MQEvent.class))
                .namespace("production")
                .topic("orders")
                .supports(Collections.singletonList("*"))
                .build();
        MQEvent original = new MQEvent();
        original.setTopic("orders");
        original.setMsgId("configured-namespace-id");
        try (DisruptorMQClient client = new DisruptorMQClient(properties)) {
            client.init(Collections.singletonList(listener), properties, new JsonMQEventSerialization(), null);
            client.initProducer(properties).accept(original);
            DisruptorEvent queued = client.getRingBuffer().get(client.getRingBuffer().getCursor());
            assertTrue(target.delivered.await(5, TimeUnit.SECONDS),
                    "Expected listener route production.orders but queued " + queued.getRouteExpression());
            assertEquals("production", queued.getNamespace());
            assertEquals("orders", queued.getTopic());
            assertEquals("production.orders", queued.getRouteExpression());
            assertEquals("configured-namespace-id", queued.getMessageId());
        } finally {
            BaseContext.clear();
        }
        assertEquals(1, target.calls.get());
        assertNotSame(original, target.received);
        assertEquals("orders", target.received.getTopic());
        assertEquals("configured-namespace-id", target.received.getMsgId());
    }

    /**
     * \u9a8c\u8bc1<code>target.delivered.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>queued.getRouteExpression()</code> \u7b49\u4e8e <code>"production.orders"</code>\uff0c<code>target.received.getMsgId()</code> \u7b49\u4e8e <code>"global-namespace-id"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void shouldDeliverUsingConfiguredNamespaceWhenListenerAndEventNamespacesAreEmpty() throws Exception {
        DisruptorMQProperties properties = new DisruptorMQProperties();
        properties.setEnabled(true);
        properties.setBroker("disruptor");
        properties.setNamespace("production");
        properties.setBufferSize(8);
        RecordingListener target = new RecordingListener();
        MQListener listener = MQListener.builder()
                .bean(target)
                .method(RecordingListener.class.getMethod("onEvent", MQEvent.class))
                .namespace("")
                .topic("orders")
                .supports(Collections.singletonList("*"))
                .build();
        MQEvent original = new MQEvent();
        original.setNamespace("");
        original.setTopic("orders");
        original.setMsgId("global-namespace-id");
        try (DisruptorMQClient client = new DisruptorMQClient(properties)) {
            client.init(Collections.singletonList(listener), properties, new JsonMQEventSerialization(), null);
            client.initProducer(properties).accept(original);
            DisruptorEvent queued = client.getRingBuffer().get(client.getRingBuffer().getCursor());
            assertTrue(target.delivered.await(5, TimeUnit.SECONDS),
                    "Listener must inherit configured namespace for queued route " + queued.getRouteExpression());
            assertEquals("production.orders", queued.getRouteExpression());
            assertEquals("global-namespace-id", queued.getMessageId());
        } finally {
            BaseContext.clear();
        }
        assertEquals("", listener.getNamespace());
        assertEquals(1, target.calls.get());
        assertNotSame(original, target.received);
        assertEquals("orders", target.received.getTopic());
        assertEquals("global-namespace-id", target.received.getMsgId());
    }

    /** \u9a8c\u8bc1<code>acknowledgment.isAcknowledged()</code>\u4e3a\u771f\uff0c<code>ringBuffer.get(ringBuffer.getCursor()).getMessageId()</code> \u7b49\u4e8e <code>"stable-id"</code>\uff0c<code>ringBuffer.getCursor()</code> \u7b49\u4e8e <code>republishedSequence</code>\u3002 */
    @Test
    void shouldRetainStableMessageIdWhenNackRequeuesLocally() {
        DisruptorEvent event = new DisruptorEvent();
        event.setMessageId("stable-id");
        event.setTopic("orders");
        event.setNamespace("production");
        event.setTag("paid");
        RingBuffer<DisruptorEvent> ringBuffer = RingBuffer.createSingleProducer(DisruptorEvent::new, 8);
        DisruptorAcknowledgment acknowledgment = new DisruptorAcknowledgment(event, ringBuffer, 3L);

        acknowledgment.nack(true);

        assertTrue(acknowledgment.isAcknowledged());
        assertEquals("stable-id", ringBuffer.get(ringBuffer.getCursor()).getMessageId());
        assertEquals("production.orders.paid", ringBuffer.get(ringBuffer.getCursor()).getRouteExpression());
        long republishedSequence = ringBuffer.getCursor();
        acknowledgment.nack(true);
        assertEquals(republishedSequence, ringBuffer.getCursor());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>client.close()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldCloseOwnedDisruptorOnlyOnce() {
        DisruptorMQClient client = new DisruptorMQClient(new DisruptorMQProperties());
        client.close();
        client.close();
    }

    /**
     * 记录真实消费者反序列化后的事件。
     */
    public static class RecordingListener {
        /** \u534f\u8c03\u6d4b\u8bd5\u7ebf\u7a0b\u5728\u6307\u5b9a\u6267\u884c\u9636\u6bb5\u7b49\u5f85\u6216\u7ee7\u7eed\u7684\u95e8\u95e9\u3002 */
        private final CountDownLatch delivered = new CountDownLatch(1);
        /** \u8bb0\u5f55\u88ab\u6d4b\u5165\u53e3\u5b9e\u9645\u89e6\u53d1\u7684\u8c03\u7528\u3002 */
        private final AtomicInteger calls = new AtomicInteger();
        /** \u5f53\u524d\u6d4b\u8bd5\u63a5\u6536\u7684\u6570\u636e\uff0c\u4f9b\u65ad\u8a00\u6838\u5bf9\u6295\u9012\u5185\u5bb9\u6216\u6b21\u6570\u3002 */
        private MQEvent received;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>received = event</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        public void onEvent(MQEvent event) {
            received = event;
            calls.incrementAndGet();
            delivered.countDown();
        }
    }
}
