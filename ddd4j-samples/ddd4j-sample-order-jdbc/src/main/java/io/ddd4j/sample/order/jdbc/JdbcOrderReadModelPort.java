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

import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL JDBC 订单读模型适配器。
 *
 * <p>写入时复用订单事务连接，查询时使用独立的只读连接，避免读模型成为聚合表的隐式视图。
 */
public final class JdbcOrderReadModelPort implements OrderReadModelPort {

    private final JdbcOrderTransactionPort transaction;

    public JdbcOrderReadModelPort(JdbcOrderTransactionPort transaction) {
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
    }

    @Override
    public void project(OrderReadModel order) {
        OrderReadModel readModel = Objects.requireNonNull(order, "order must not be null");
        String sql = "INSERT INTO sample_order_read_models "
                + "(id, order_no, buyer_id, buyer_name, status, total_amount) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET order_no = EXCLUDED.order_no, buyer_id = EXCLUDED.buyer_id, "
                + "buyer_name = EXCLUDED.buyer_name, status = EXCLUDED.status, total_amount = EXCLUDED.total_amount";
        try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
            statement.setString(1, readModel.id());
            statement.setString(2, readModel.orderNo());
            statement.setString(3, readModel.buyerId());
            statement.setString(4, readModel.buyerName());
            statement.setString(5, readModel.status().name());
            statement.setBigDecimal(6, readModel.totalAmount());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to project order read model", exception);
        }
    }

    @Override
    public Optional<OrderReadModel> findProjectionById(String orderId) {
        return transaction.query(connection -> findById(connection, orderId));
    }

    @Override
    public List<OrderReadModel> query(OrderQuery query) {
        OrderQuery criteria = Objects.requireNonNull(query, "query must not be null");
        return transaction.query(connection -> query(connection, criteria));
    }

    private Optional<OrderReadModel> findById(Connection connection, String orderId) throws SQLException {
        String sql = "SELECT * FROM sample_order_read_models WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(toReadModel(rows)) : Optional.empty();
            }
        }
    }

    private List<OrderReadModel> query(Connection connection, OrderQuery query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM sample_order_read_models WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (Objects.nonNull(query.buyerId())) {
            sql.append(" AND buyer_id = ?");
            parameters.add(query.buyerId());
        }
        if (Objects.nonNull(query.status())) {
            sql.append(" AND status = ?");
            parameters.add(query.status().name());
        }
        sql.append(" ORDER BY order_no OFFSET ? LIMIT ?");
        parameters.add((query.page() - 1) * query.size());
        parameters.add(query.size());

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            List<OrderReadModel> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(toReadModel(rows));
                }
            }
            return result;
        }
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    private OrderReadModel toReadModel(ResultSet row) throws SQLException {
        return new OrderReadModel(
                row.getString("id"),
                row.getString("order_no"),
                row.getString("buyer_id"),
                row.getString("buyer_name"),
                OrderStatus.valueOf(row.getString("status")),
                row.getBigDecimal("total_amount")
        );
    }
}
