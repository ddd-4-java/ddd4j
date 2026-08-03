package io.ddd4j.sample.order.jdbc;

import io.ddd4j.sample.order.domain.Money;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderLine;
import io.ddd4j.sample.order.domain.OrderRepository;
import io.ddd4j.sample.order.domain.OrderStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL JDBC 订单聚合仓储。 */
public final class JdbcOrderRepository implements OrderRepository {

    private final JdbcOrderTransactionPort transaction;

    public JdbcOrderRepository(JdbcOrderTransactionPort transaction) {
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
    }

    @Override
    public void save(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        String orderSql = "INSERT INTO sample_orders (id, order_no, buyer_id, buyer_name, status, total_amount) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET buyer_name = EXCLUDED.buyer_name, "
                + "status = EXCLUDED.status, total_amount = EXCLUDED.total_amount";
        try (PreparedStatement statement = transaction.connection().prepareStatement(orderSql)) {
            statement.setString(1, order.id());
            statement.setString(2, order.orderNo());
            statement.setString(3, order.buyerId());
            statement.setString(4, order.buyerName());
            statement.setString(5, order.status().name());
            statement.setBigDecimal(6, order.totalAmount().amount());
            statement.executeUpdate();
            try (PreparedStatement delete = transaction.connection().prepareStatement(
                    "DELETE FROM sample_order_lines WHERE order_id = ?")) {
                delete.setString(1, order.id());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = transaction.connection().prepareStatement(
                    "INSERT INTO sample_order_lines (id, order_id, goods_id, goods_name, quantity, unit_price, currency) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (OrderLine line : order.lines()) {
                    insert.setString(1, line.id());
                    insert.setString(2, order.id());
                    insert.setString(3, line.goodsId());
                    insert.setString(4, line.goodsName());
                    insert.setInt(5, line.quantity());
                    insert.setBigDecimal(6, line.unitPrice().amount());
                    insert.setString(7, line.unitPrice().currency());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save order", exception);
        }
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return transaction.query(connection -> find(connection, "SELECT * FROM sample_orders WHERE id = ?", orderId));
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        return transaction.query(connection -> find(connection, "SELECT * FROM sample_orders WHERE order_no = ?", orderNo));
    }

    @Override
    public List<Order> findAll(int offset, int limit) {
        return transaction.query(connection -> {
            List<Order> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM sample_orders ORDER BY order_no OFFSET ? LIMIT ?")) {
                statement.setInt(1, offset);
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(toOrder(connection, rows));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public long count() {
        return transaction.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM sample_orders");
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        });
    }

    private Optional<Order> find(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(toOrder(connection, rows)) : Optional.empty();
            }
        }
    }

    private Order toOrder(Connection connection, ResultSet row) throws SQLException {
        String id = row.getString("id");
        List<OrderLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM sample_order_lines WHERE order_id = ? ORDER BY id")) {
            statement.setString(1, id);
            try (ResultSet lineRows = statement.executeQuery()) {
                while (lineRows.next()) {
                    lines.add(new OrderLine(
                            lineRows.getString("id"),
                            lineRows.getString("goods_id"),
                            lineRows.getString("goods_name"),
                            lineRows.getInt("quantity"),
                            new Money(lineRows.getBigDecimal("unit_price"), lineRows.getString("currency"))));
                }
            }
        }
        return new Order(id, row.getString("order_no"), row.getString("buyer_id"), row.getString("buyer_name"),
                OrderStatus.valueOf(row.getString("status")), lines);
    }
}
