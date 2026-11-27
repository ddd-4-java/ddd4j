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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Inbox 去重、处理失败及观察器回调契约。 */
class MQInboxProcessorTest {

    /** \u9a8c\u8bc1<code>handled.get()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>store.lastKey</code> \u7b49\u4e8e <code>"order-read-model:message-1"</code>\uff0c<code>observer.inboxOutcomes</code> \u7b49\u4e8e <code>Arrays.asList("processed", "duplicate")</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void process_shouldRejectBlankConsumerAndMessageIdentity() {
        InMemoryInboxStore store = new InMemoryInboxStore();

        assertThrows(IllegalArgumentException.class, () -> new MQInboxProcessor(store, " "));
        MQInboxProcessor processor = new MQInboxProcessor(store, "consumer");
        assertThrows(IllegalArgumentException.class, () -> processor.process("", Instant.EPOCH, () -> {
        }));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>observer.inboxOutcomes</code> \u7b49\u4e8e <code>Collections.singletonList("failed")</code>\u3002 */
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

    /** \u5b9e\u73b0 <code>MQInboxStore</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>recordIfAbsent()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class InMemoryInboxStore implements MQInboxStore {

        /** \u5939\u5177\u4fdd\u5b58\u7684\u6e90\u8bb0\u5f55\u6216\u89c2\u5bdf\u5230\u7684\u6267\u884c\u7ed3\u679c\u3002 */
        private final Set<String> records = new HashSet<>();
        /** \u6700\u8fd1\u4e00\u6b21\u56de\u8c03\u8bb0\u5f55\u7684\u4e1a\u52a1\u6216\u6620\u5c04\u952e\u3002 */
        private String lastKey;

        /**
         * 在收件箱中首次登记消息处理记录。
         *
         * <p>测试夹具会调用 <code>records.add(lastKey)</code>。</p>
         *
         * @param consumerId 稳定消费者标识
         * @param messageId 生产端传入的稳定消息标识
         * @param processedAt 处理开始时间
         * @return 实际返回<code>records.add(lastKey)</code>
         */
        @Override
        public boolean recordIfAbsent(String consumerId, String messageId, Instant processedAt) {
            lastKey = consumerId + ':' + messageId;
            return records.add(lastKey);
        }
    }

    /** \u8bb0\u5f55\u6295\u9012\u72b6\u6001\u901a\u77e5\u4ee5\u9a8c\u8bc1\u53ef\u89c2\u6d4b\u6027\u548c\u5931\u8d25\u8def\u5f84\u3002 */
    private static final class RecordingObserver implements MQDeliveryObserver {

        /** \u8bb0\u5f55\u6536\u4ef6\u7bb1\u5904\u7406\u89c2\u5bdf\u5668\u62a5\u544a\u7684\u5404\u6b21\u7ed3\u679c\u3002 */
        private final List<String> inboxOutcomes = new ArrayList<>();

        /**
         * Inbox 首次成功处理消息。
         *
         * <p>测试夹具会调用 <code>inboxOutcomes.add("processed")</code>。</p>
         *
         * @param consumerId 消费者标识
         * @param messageId 稳定消息标识
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
         * @param messageId 稳定消息标识
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
         * @param messageId 稳定消息标识
         */
        @Override
        public void onInboxFailed(String consumerId, String messageId) {
            inboxOutcomes.add("failed");
        }
    }
}
