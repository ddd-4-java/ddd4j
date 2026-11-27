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

/**
 * 验证 <code>DisruptorAdapterContract</code> 在 <code>shouldDeliverRawTopicExactlyOnce</code>、<code>shouldDeliverUsingConfiguredNamespace</code>、<code>shouldRetainStableMessageId</code> 等场景中的行为。
 */
class DisruptorAdapterContractTest {

    /**
     * 验证<code>target.delivered.await(5, TimeUnit.SECONDS)</code>为真，<code>queued.getTopic()</code> 等于 <code>"orders"</code>，<code>target.received.getMsgId()</code> 等于 <code>"stable-route-id"</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>target.delivered.await(5, TimeUnit.SECONDS)</code>为真，<code>queued.getNamespace()</code> 等于 <code>"production"</code>，<code>target.received.getMsgId()</code> 等于 <code>"configured-namespace-id"</code>。
     *
     * @throws Exception 等待并发操作时失败
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
     * 验证<code>target.delivered.await(5, TimeUnit.SECONDS)</code>为真，<code>queued.getRouteExpression()</code> 等于 <code>"production.orders"</code>，<code>target.received.getMsgId()</code> 等于 <code>"global-namespace-id"</code>。
     *
     * @throws Exception 等待并发操作时失败
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

    /**
     * 验证<code>acknowledgment.isAcknowledged()</code>为真，<code>ringBuffer.get(ringBuffer.getCursor()).getMessageId()</code> 等于 <code>"stable-id"</code>，<code>ringBuffer.getCursor()</code> 等于 <code>republishedSequence</code>。
     */
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

    /**
     * 在测试框架中执行 <code>client.close()</code>，验证该场景。
     */
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
        /**
         * 协调测试线程在指定执行阶段等待或继续的门闩。
         */
        private final CountDownLatch delivered = new CountDownLatch(1);
        /**
         * 记录被测入口实际触发的调用。
         */
        private final AtomicInteger calls = new AtomicInteger();
        /**
         * 当前测试接收的数据，供断言核对投递内容或次数。
         */
        private MQEvent received;

        /**
         * 根据本次调用更新 <code>received = event</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        public void onEvent(MQEvent event) {
            received = event;
            calls.incrementAndGet();
            delivered.countDown();
        }
    }
}
