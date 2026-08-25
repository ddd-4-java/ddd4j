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

import io.ddd4j.sample.order.application.OrderTransactionPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 基于 JDBC 的订单事务边界。
 *
 * <p>同一线程内的订单仓储、Outbox 和读模型适配器通过 {@link #connection()} 复用同一数据库连接。
 */
public final class JdbcOrderTransactionPort implements OrderTransactionPort {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> currentConnection = new ThreadLocal<>();

    public JdbcOrderTransactionPort(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public void execute(Runnable operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        if (Objects.nonNull(currentConnection.get())) {
            operation.run();
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean transactionStarted = false;
            try {
                connection.setAutoCommit(false);
                transactionStarted = true;
                currentConnection.set(connection);
                operation.run();
                connection.commit();
            } catch (SQLException exception) {
                if (transactionStarted) {
                    rollback(connection, exception);
                }
                throw new IllegalStateException("Unable to execute order transaction", exception);
            } catch (RuntimeException | Error exception) {
                if (transactionStarted) {
                    rollback(connection, exception);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to acquire or close order transaction connection", exception);
        } finally {
            currentConnection.remove();
        }
    }

    /**
     * 返回当前订单事务绑定的连接。
     *
     * @return 当前 JDBC 连接
     */
    public Connection connection() {
        Connection connection = currentConnection.get();
        if (Objects.isNull(connection)) {
            throw new IllegalStateException("Order JDBC operation must execute inside OrderTransactionPort");
        }
        return connection;
    }

    /**
     * 在当前事务连接或一个短生命周期查询连接上执行只读操作。
     *
     * @param operation JDBC 查询函数
     * @param <T> 返回类型
     * @return 查询结果
     */
    public <T> T query(SqlQuery<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        Connection current = currentConnection.get();
        if (Objects.nonNull(current)) {
            return apply(operation, current);
        }
        try (Connection connection = dataSource.getConnection()) {
            return apply(operation, connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute order query", exception);
        }
    }

    private <T> T apply(SqlQuery<T> operation, Connection connection) {
        try {
            return operation.apply(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute order query", exception);
        }
    }

    private void rollback(Connection connection, Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            originalFailure.addSuppressed(exception);
        }
    }

    @FunctionalInterface
    public interface SqlQuery<T> {
        T apply(Connection connection) throws SQLException;
    }
}
