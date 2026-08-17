package io.ddd4j.sample.order.jdbc;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL Outbox 适配器。
 *
 * <p>消息和订单写侧由同一个 {@link JdbcOrderTransactionPort} 管理，发布状态只在 broker 成功后改变。
 */
public final class JdbcOutboxPort implements OutboxPort {

    private final JdbcOrderTransactionPort transaction;
    private final ObjectMapper objectMapper;

    public JdbcOutboxPort(JdbcOrderTransactionPort transaction, ObjectMapper objectMapper) {
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void append(List<OutboxMessage> messages) {
        Objects.requireNonNull(messages, "messages must not be null");
        String sql = "INSERT INTO sample_order_outbox "
                + "(id, aggregate_id, event_type, payload, occurred_at, status, available_at, attempts) "
                + "VALUES (?, ?, ?, ?::jsonb, ?, 'PENDING', ?, 0)";
        try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
            for (OutboxMessage message : messages) {
                statement.setString(1, message.id());
                statement.setString(2, message.aggregateId());
                statement.setString(3, message.eventType());
                statement.setString(4, objectMapper.writeValueAsString(message.payload()));
                statement.setTimestamp(5, Timestamp.from(message.occurredAt()));
                statement.setTimestamp(6, Timestamp.from(message.occurredAt()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException | JacksonException exception) {
            throw new IllegalStateException("Unable to append order outbox messages", exception);
        }
    }

    @Override
    public List<OutboxMessage> pending(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        String sql = "SELECT id, aggregate_id, event_type, payload, occurred_at FROM sample_order_outbox "
                + "WHERE status = 'PENDING' ORDER BY occurred_at FOR UPDATE SKIP LOCKED LIMIT ?";
        List<OutboxMessage> messages = new ArrayList<>();
        try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(new OutboxMessage(resultSet.getString("id"), resultSet.getString("aggregate_id"),
                            resultSet.getString("event_type"), objectMapper.readTree(resultSet.getString("payload")),
                            resultSet.getTimestamp("occurred_at").toInstant()));
                }
            }
            return messages;
        } catch (SQLException | JacksonException exception) {
            throw new IllegalStateException("Unable to fetch pending order outbox messages", exception);
        }
    }

    @Override
    public void markPublished(String messageId) {
        updateStatus(messageId, "PUBLISHED", null);
    }

    @Override
    public void markFailed(String messageId, String reason) {
        updateStatus(messageId, "PENDING", reason);
    }

    private void updateStatus(String messageId, String status, String failureReason) {
        String sql = "UPDATE sample_order_outbox SET status = ?, attempts = attempts + 1, last_error = ?, published_at = "
                + "CASE WHEN ? = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END WHERE id = ?";
        try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, failureReason);
            statement.setString(3, status);
            statement.setString(4, messageId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update order outbox status", exception);
        }
    }
}
