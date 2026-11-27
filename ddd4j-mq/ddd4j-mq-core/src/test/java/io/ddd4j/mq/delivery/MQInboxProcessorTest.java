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
package io.ddd4j.mq.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Inbox 去重、处理失败及观察器回调契约。
 */
class MQInboxProcessorTest {

    /**
     * 验证<code>handled.get()</code> 等于 <code>1</code>，<code>store.lastKey</code> 等于 <code>"order-read-model:message-1"</code>，<code>observer.inboxOutcomes</code> 等于 <code>Arrays.asList("processed", "duplicate")</code>。
     */
    @Test
    void process_shouldHandleFirstDeliveryAndSkipDuplicate() {
        InMemoryInboxStore store = new InMemoryInboxStore();
        RecordingObserver observer = new RecordingObserver();
        MQInboxProcessor processor = new MQInboxProcessor(store, "order-read-model", observer);
        AtomicInteger handled = new AtomicInteger();

        assertTrue(processor.process("message-1", Instant.EPOCH, handled::incrementAndGet));
        assertFalse(processor.process("message-1", Instant.EPOCH, handled::incrementAndGet));

        assertEquals(1, handled.get());
        assertEquals("order-read-model:message-1", store.lastKey);
        assertEquals(Arrays.asList("processed", "duplicate"), observer.inboxOutcomes);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void process_shouldRejectBlankConsumerAndMessageIdentity() {
        InMemoryInboxStore store = new InMemoryInboxStore();

        assertThrows(IllegalArgumentException.class, () -> new MQInboxProcessor(store, " "));
        MQInboxProcessor processor = new MQInboxProcessor(store, "consumer");
        assertThrows(IllegalArgumentException.class, () -> processor.process("", Instant.EPOCH, () -> {
        }));
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>observer.inboxOutcomes</code> 等于 <code>Collections.singletonList("failed")</code>。
     */
    @Test
    void process_shouldNotifyFailureAndRethrowBusinessException() {
        InMemoryInboxStore store = new InMemoryInboxStore();
        RecordingObserver observer = new RecordingObserver();
        MQInboxProcessor processor = new MQInboxProcessor(store, "consumer", observer);

        assertThrows(IllegalStateException.class,
                () -> processor.process("message-1", Instant.EPOCH,
                        () -> {
                            throw new IllegalStateException("business failed");
                        }));

        assertEquals(Collections.singletonList("failed"), observer.inboxOutcomes);
    }

    /**
     * 实现 <code>MQInboxStore</code>，供测试调用 <code>recordIfAbsent()</code> 并观察结果。
     */
    private static final class InMemoryInboxStore implements MQInboxStore {

        /**
         * 夹具保存的源记录或观察到的执行结果。
         */
        private final Set<String> records = new HashSet<>();
        /**
         * 最近一次回调记录的业务或映射键。
         */
        private String lastKey;

        /**
         * 在收件箱中首次登记消息处理记录。
         *
         * <p>测试夹具会调用 <code>records.add(lastKey)</code>。</p>
         *
         * @param consumerId  稳定消费者标识
         * @param messageId   生产端传入的稳定消息标识
         * @param processedAt 处理开始时间
         * @return 实际返回<code>records.add(lastKey)</code>
         */
        @Override
        public boolean recordIfAbsent(String consumerId, String messageId, Instant processedAt) {
            lastKey = consumerId + ':' + messageId;
            return records.add(lastKey);
        }
    }

    /**
     * 记录投递状态通知以验证可观测性和失败路径。
     */
    private static final class RecordingObserver implements MQDeliveryObserver {

        /**
         * 记录收件箱处理观察器报告的各次结果。
         */
        private final List<String> inboxOutcomes = new ArrayList<>();

        /**
         * Inbox 首次成功处理消息。
         *
         * <p>测试夹具会调用 <code>inboxOutcomes.add("processed")</code>。</p>
         *
         * @param consumerId 消费者标识
         * @param messageId  稳定消息标识
         */
        @Override
        public void onInboxProcessed(String consumerId, String messageId) {
            inboxOutcomes.add("processed");
        }

        /**
         * Inbox 识别到已处理的重复消息。
         *
         * <p>测试夹具会调用 <code>inboxOutcomes.add("duplicate")</code>。</p>
         *
         * @param consumerId 消费者标识
         * @param messageId  稳定消息标识
         */
        @Override
        public void onInboxDuplicate(String consumerId, String messageId) {
            inboxOutcomes.add("duplicate");
        }

        /**
         * Inbox 记录或业务处理失败，调用方应保持不 ACK 以触发重投。
         *
         * <p>测试夹具会调用 <code>inboxOutcomes.add("failed")</code>。</p>
         *
         * @param consumerId 消费者标识
         * @param messageId  稳定消息标识
         */
        @Override
        public void onInboxFailed(String consumerId, String messageId) {
            inboxOutcomes.add("failed");
        }
    }
}
