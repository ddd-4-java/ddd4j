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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcOrderTransactionPortTest {

    @Test
    void shouldRollBackWhenBusinessOperationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        IllegalStateException failure = new IllegalStateException("business failure");

        assertThatThrownBy(() -> transaction.execute(() -> {
            throw failure;
        })).isSameAs(failure);

        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
    }

    @Test
    void shouldRollBackWhenCommitFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException failure = new SQLException("commit failure");
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(failure).when(connection).commit();
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);

        assertThatThrownBy(() -> transaction.execute(() -> {
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to execute order transaction")
                .hasCause(failure);

        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
    }
}
