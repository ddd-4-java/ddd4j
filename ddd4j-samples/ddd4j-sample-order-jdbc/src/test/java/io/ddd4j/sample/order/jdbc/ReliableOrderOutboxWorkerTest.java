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
package io.ddd4j.sample.order.jdbc;

import org.junit.jupiter.api.Test;
import io.ddd4j.mq.delivery.MQDeliveryPolicy;
import io.ddd4j.mq.delivery.MQOutboxStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class ReliableOrderOutboxWorkerTest {

    @Test
    void shouldDispatchOnScheduleAndStopAfterClose() throws Exception {
        MQOutboxStore store = mock(MQOutboxStore.class);
        CountDownLatch firstDispatch = new CountDownLatch(1);
        AtomicInteger dispatches = new AtomicInteger();
        when(store.claim(anyString(), any(Instant.class), eq(5), any(MQDeliveryPolicy.class))).thenAnswer(invocation -> {
            dispatches.incrementAndGet();
            firstDispatch.countDown();
            return Collections.emptyList();
        });
        ReliableOrderOutboxPublisher publisher = publisher(store);

        ReliableOrderOutboxWorker worker = new ReliableOrderOutboxWorker(publisher, 5, Duration.ofMillis(20));
        worker.start();
        assertThat(firstDispatch.await(2, TimeUnit.SECONDS)).isTrue();
        worker.close();
        int countAfterClose = dispatches.get();
        TimeUnit.MILLISECONDS.sleep(100);
        assertThat(dispatches.get()).isEqualTo(countAfterClose);
        verify(store, atLeast(1)).claim(anyString(), any(Instant.class), eq(5), any(MQDeliveryPolicy.class));
    }

    @Test
    void shouldKeepSchedulingAfterPublisherFailure() throws Exception {
        MQOutboxStore store = mock(MQOutboxStore.class);
        CountDownLatch attempts = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        when(store.claim(anyString(), any(Instant.class), eq(1), any(MQDeliveryPolicy.class))).thenAnswer(invocation -> {
            attempts.countDown();
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("broker unavailable");
            }
            return Collections.emptyList();
        });
        ReliableOrderOutboxPublisher publisher = publisher(store);

        try (ReliableOrderOutboxWorker worker = new ReliableOrderOutboxWorker(
                publisher, 1, Duration.ofMillis(20))) {
            worker.start();
            assertThat(attempts.await(2, TimeUnit.SECONDS)).isTrue();
        }
        verify(store, atLeast(2)).claim(anyString(), any(Instant.class), eq(1), any(MQDeliveryPolicy.class));
    }

    @Test
    void shouldRejectInvalidScheduleAndRestartAfterClose() {
        MQOutboxStore store = mock(MQOutboxStore.class);
        ReliableOrderOutboxPublisher publisher = publisher(store);
        assertThatThrownBy(() -> new ReliableOrderOutboxWorker(publisher, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReliableOrderOutboxWorker(publisher, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        ReliableOrderOutboxWorker worker = new ReliableOrderOutboxWorker(publisher, 1, Duration.ofSeconds(1));
        worker.close();
        assertThatThrownBy(worker::start).isInstanceOf(IllegalStateException.class);
        verify(store, times(0)).claim(anyString(), any(Instant.class), eq(1), any(MQDeliveryPolicy.class));
    }

    private ReliableOrderOutboxPublisher publisher(MQOutboxStore store) {
        return new ReliableOrderOutboxPublisher(store, id -> null, message -> { },
                "order-worker", Clock.systemUTC());
    }
}
