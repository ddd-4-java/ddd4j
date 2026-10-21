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
package io.ddd4j.mq.tdmq;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDMQ 确认结果契约测试。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class TdmqAcknowledgmentContractTest {

    @Test
    void concurrentAckAndNackInvokeProtocolOnlyOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        TdmqAcknowledgment acknowledgment = new TdmqAcknowledgment("stable-id", null, 6L, requeue -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("callback timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> acknowledgment.ack());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                assertThrows(IllegalStateException.class, () -> acknowledgment.nack(true));
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(1, calls.get());
            assertTrue(acknowledgment.isAcknowledged());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedCallbackLeavesAcknowledgmentRetryable() {
        AtomicInteger calls = new AtomicInteger();
        TdmqAcknowledgment acknowledgment = new TdmqAcknowledgment("stable-id", null, 6L, requeue -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("protocol failed");
            }
        });

        assertThrows(IllegalStateException.class, acknowledgment::ack);
        assertFalse(acknowledgment.isAcknowledged());
        acknowledgment.nack(true);
        assertEquals(2, calls.get());
        assertTrue(acknowledgment.isAcknowledged());
    }

    @Test
    void shouldForwardRequeueIntentToProtocolAdapter() {
        AtomicReference<Boolean> requeue = new AtomicReference<>();
        TdmqAcknowledgment acknowledgment = new TdmqAcknowledgment("stable-id", null, 6L, requeue::set);

        acknowledgment.nack(true);

        assertEquals(Boolean.TRUE, requeue.get());
        assertTrue(acknowledgment.isAcknowledged());
    }
}
