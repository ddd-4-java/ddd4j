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

import io.ddd4j.sample.order.application.OutboxMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JdbcOutboxPortReadTest {

    @Test
    void shouldRestoreTheOriginalEnvelopeAndCloseItsReadConnection() throws Exception {
        Instant occurredAt = Instant.parse("2026-10-12T00:00:00Z");
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString("id")).thenReturn("message-1");
        when(rows.getString("aggregate_id")).thenReturn("order-1");
        when(rows.getString("event_type")).thenReturn("OrderPaidEvent");
        when(rows.getString("payload")).thenReturn("{\"paid\":true}");
        when(rows.getTimestamp("occurred_at")).thenReturn(Timestamp.from(occurredAt));

        OutboxMessage message = new JdbcOutboxPort(new JdbcOrderTransactionPort(dataSource),
                new ObjectMapper()).findById("message-1");

        assertThat(message.id()).isEqualTo("message-1");
        assertThat(message.aggregateId()).isEqualTo("order-1");
        assertThat(message.eventType()).isEqualTo("OrderPaidEvent");
        assertThat(message.payload().toString()).contains("paid");
        assertThat(message.occurredAt()).isEqualTo(occurredAt);
        verify(statement).setString(1, "message-1");
        verify(connection).close();
    }
}
