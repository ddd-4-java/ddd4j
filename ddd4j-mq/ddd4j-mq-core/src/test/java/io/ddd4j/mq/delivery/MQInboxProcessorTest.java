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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class MQInboxProcessorTest {

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
        assertEquals(List.of("processed", "duplicate"), observer.inboxOutcomes);
    }

    @Test
    void process_shouldRejectBlankConsumerAndMessageIdentity() {
        InMemoryInboxStore store = new InMemoryInboxStore();

        assertThrows(IllegalArgumentException.class, () -> new MQInboxProcessor(store, " "));
        MQInboxProcessor processor = new MQInboxProcessor(store, "consumer");
        assertThrows(IllegalArgumentException.class, () -> processor.process("", Instant.EPOCH, () -> {
        }));
    }

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

        assertEquals(List.of("failed"), observer.inboxOutcomes);
    }

    /**
     * 基于内存集合的去重存储，记录最后一次写入的去重键用于断言。
     */
    private static final class InMemoryInboxStore implements MQInboxStore {
/**
 * HashSet<>() 方法。
 * @return 方法执行结果。
 */

        private final Set<String> records = new HashSet<>();
        private String lastKey;

        /**
         * recordIfAbsent() 方法。
         * @param consumerId 字符串参数。
         * @param messageId 字符串参数。
         * @param processedAt processedAt 参数。
         * @return 判断结果。
         */
        @Override
        public boolean recordIfAbsent(String consumerId, String messageId, Instant processedAt) {
            lastKey = consumerId + ':' + messageId;
            /**
             * records.add() 方法。
             * @param lastKey lastKey 参数。
             * @return 方法执行结果。
             */
            return records.add(lastKey);
        }
    }

    /**
     * 记录收件箱处理结果的观察器，把回调按顺序追加为可断言的标签序列。
     */
    private static final class RecordingObserver implements MQDeliveryObserver {

        private final List<String> inboxOutcomes = new ArrayList<>();

        /**
         * onInboxProcessed() 方法。
         * @param consumerId 字符串参数。
         * @param messageId 字符串参数。
         */
        @Override
        public void onInboxProcessed(String consumerId, String messageId) {
            inboxOutcomes.add("processed");
        }

        /**
         * onInboxDuplicate() 方法。
         * @param consumerId 字符串参数。
         * @param messageId 字符串参数。
         */
        @Override
        public void onInboxDuplicate(String consumerId, String messageId) {
            inboxOutcomes.add("duplicate");
        }

        /**
         * onInboxFailed() 方法。
         * @param consumerId 字符串参数。
         * @param messageId 字符串参数。
         */
        @Override
        public void onInboxFailed(String consumerId, String messageId) {
            inboxOutcomes.add("failed");
        }
    }
}
