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

import io.ddd4j.mq.delivery.MQDeliveryPolicy;
import io.ddd4j.mq.delivery.MQOutboxRecord;
import io.ddd4j.mq.delivery.MQOutboxStore;
import io.ddd4j.sample.order.application.OutboxMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReliableOrderOutboxPublisherTest {

    @Test
    void shouldSendTheOriginalOrderEnvelopeThroughTheCommonDispatcher() {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        MQOutboxStore store = mock(MQOutboxStore.class);
        MQOutboxRecord record = MQOutboxRecord.pending("message-1", "OrderPaidEvent", "{}",
                Collections.emptyMap(), now);
        when(store.claim(eq("order-worker"), eq(now), eq(1), any(MQDeliveryPolicy.class)))
                .thenReturn(Collections.singletonList(record));
        when(store.markPublished("message-1", "order-worker", now)).thenReturn(true);
        OutboxMessage original = new OutboxMessage("message-1", "order-1", "OrderPaidEvent", "payload", now);
        AtomicReference<OutboxMessage> sent = new AtomicReference<>();

        ReliableOrderOutboxPublisher publisher = new ReliableOrderOutboxPublisher(store,
                id -> original, sent::set, "order-worker", Clock.fixed(now, ZoneOffset.UTC));

        assertThat(publisher.publishPending(1).published()).isEqualTo(1);
        assertThat(sent.get()).isSameAs(original);
        verify(store).markPublished("message-1", "order-worker", now);
    }

    @Test
    void shouldRescheduleWhenTheExistingPublisherFails() {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        MQOutboxStore store = mock(MQOutboxStore.class);
        MQOutboxRecord record = MQOutboxRecord.pending("message-2", "OrderPaidEvent", "{}",
                Collections.emptyMap(), now);
        when(store.claim(eq("order-worker"), eq(now), eq(1), any(MQDeliveryPolicy.class)))
                .thenReturn(Collections.singletonList(record));
        when(store.reschedule(eq("message-2"), eq("order-worker"), eq(now),
                eq("broker unavailable"), any(MQDeliveryPolicy.class))).thenReturn(true);
        OutboxMessage original = new OutboxMessage("message-2", "order-2", "OrderPaidEvent", "payload", now);
        ReliableOrderOutboxPublisher publisher = new ReliableOrderOutboxPublisher(store,
                id -> original, message -> {
                    throw new IllegalStateException("broker unavailable");
                }, "order-worker", Clock.fixed(now, ZoneOffset.UTC));

        assertThat(publisher.publishPending(1).rescheduled()).isEqualTo(1);
        verify(store, never()).markPublished(anyString(), anyString(), any(Instant.class));
    }

    @Test
    void shouldRejectDispatchInsideTheOrderTransaction() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        JdbcOutboxPort outbox = new JdbcOutboxPort(transaction, new ObjectMapper());
        ReliableOrderOutboxPublisher publisher = new ReliableOrderOutboxPublisher(transaction, outbox,
                message -> { }, "order-worker", Clock.systemUTC());

        transaction.execute(() -> assertThatThrownBy(() -> publisher.publishPending(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Reliable order outbox dispatch must execute outside the order transaction"));

        verify(connection).commit();
        verify(connection, never()).prepareStatement(anyString());
    }
}
