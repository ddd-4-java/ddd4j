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

import io.ddd4j.mq.delivery.MQDeliveryHeaders;
import io.ddd4j.mq.delivery.MQDeliveryPolicy;
import io.ddd4j.mq.delivery.MQOutboxRecord;
import io.ddd4j.mq.delivery.MQOutboxStatus;
import io.ddd4j.mq.delivery.MQOutboxStore;
import io.ddd4j.kit.lang.StrKit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PostgreSQL 的可靠消息 Outbox 实现。
 *
 * <p>领取、确认和重试状态变更各自使用短 JDBC 事务；调用方必须在事务外执行 broker 发送。
 */
public final class JdbcMQOutboxStore implements MQOutboxStore {

    private final JdbcOrderTransactionPort transaction;

    public JdbcMQOutboxStore(JdbcOrderTransactionPort transaction) {
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
    }

    @Override
    public void append(MQOutboxRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        String sql = "INSERT INTO sample_order_outbox "
                + "(id, aggregate_id, event_type, payload, occurred_at, status, available_at, attempts, last_error) "
                + "VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
            statement.setString(1, record.messageId());
            statement.setString(2, record.messageId());
            statement.setString(3, record.destination());
            statement.setString(4, record.payload());
            statement.setTimestamp(5, Timestamp.from(record.availableAt()));
            statement.setString(6, record.status().name());
            statement.setTimestamp(7, Timestamp.from(record.availableAt()));
            statement.setInt(8, record.attempts());
            statement.setString(9, record.lastError());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to append reliable outbox record", exception);
        }
    }

    @Override
    public List<MQOutboxRecord> claim(String leaseOwner, Instant now, int limit, MQDeliveryPolicy policy) {
        if (StrKit.isBlank(leaseOwner)) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        AtomicReference<List<MQOutboxRecord>> claimed = new AtomicReference<>();
        transaction.execute(() -> claimed.set(claimInCurrentTransaction(leaseOwner, now, limit, policy)));
        return Objects.requireNonNull(claimed.get(), "claimed records must not be null");
    }

    @Override
    public boolean markPublished(String messageId, String leaseOwner, Instant publishedAt) {
        return updateWithShortTransaction(() -> {
            String sql = "UPDATE sample_order_outbox SET status = 'PUBLISHED', published_at = ?, lease_owner = NULL, "
                    + "lease_until = NULL WHERE id = ? AND status = 'LEASED' AND lease_owner = ? AND lease_until > ?";
            try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(publishedAt));
                statement.setString(2, messageId);
                statement.setString(3, leaseOwner);
                statement.setTimestamp(4, Timestamp.from(publishedAt));
                return statement.executeUpdate() == 1;
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to confirm reliable outbox message", exception);
            }
        });
    }

    @Override
    public boolean reschedule(String messageId, String leaseOwner, Instant failedAt, String lastError,
                              MQDeliveryPolicy policy) {
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        return updateWithShortTransaction(() -> rescheduleInCurrentTransaction(messageId, leaseOwner, failedAt,
                lastError, policy));
    }

    @Override
    public boolean replay(String messageId, Instant availableAt) {
        return updateWithShortTransaction(() -> {
            String sql = "UPDATE sample_order_outbox SET status = 'PENDING', available_at = ?, attempts = 0, "
                    + "last_error = NULL, lease_owner = NULL, lease_until = NULL WHERE id = ? AND status = 'DEAD'";
            try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(availableAt));
                statement.setString(2, messageId);
                return statement.executeUpdate() == 1;
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to replay reliable outbox message", exception);
            }
        });
    }

    private List<MQOutboxRecord> claimInCurrentTransaction(String leaseOwner, Instant now, int limit,
                                                            MQDeliveryPolicy policy) {
        String sql = "SELECT id, event_type, payload, available_at, attempts, last_error, published_at "
                + "FROM sample_order_outbox WHERE (status = 'PENDING' AND available_at <= ?) "
                + "OR (status = 'LEASED' AND lease_until <= ?) ORDER BY available_at FOR UPDATE SKIP LOCKED LIMIT ?";
        List<MQOutboxRecord> records = new ArrayList<>();
        Instant leaseUntil = now.plus(policy.leaseDuration());
        try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String messageId = rows.getString("id");
                    int attempts = rows.getInt("attempts") + 1;
                    lease(messageId, leaseOwner, leaseUntil, attempts);
                    records.add(new MQOutboxRecord(messageId, rows.getString("event_type"), rows.getString("payload"),
                            Map.of(MQDeliveryHeaders.MESSAGE_ID, messageId), MQOutboxStatus.LEASED,
                            rows.getTimestamp("available_at").toInstant(), leaseOwner, leaseUntil, attempts,
                            rows.getString("last_error"), toInstant(rows, "published_at")));
                }
            }
            return records;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to claim reliable outbox records", exception);
        }
    }

    private void lease(String messageId, String leaseOwner, Instant leaseUntil, int attempts) throws SQLException {
        String sql = "UPDATE sample_order_outbox SET status = 'LEASED', lease_owner = ?, lease_until = ?, attempts = ? "
                + "WHERE id = ?";
        try (PreparedStatement statement = transaction.connection().prepareStatement(sql)) {
            statement.setString(1, leaseOwner);
            statement.setTimestamp(2, Timestamp.from(leaseUntil));
            statement.setInt(3, attempts);
            statement.setString(4, messageId);
            statement.executeUpdate();
        }
    }

    private boolean rescheduleInCurrentTransaction(String messageId, String leaseOwner, Instant failedAt,
                                                   String lastError, MQDeliveryPolicy policy) {
        String lookup = "SELECT attempts FROM sample_order_outbox WHERE id = ? AND status = 'LEASED' "
                + "AND lease_owner = ? AND lease_until > ? FOR UPDATE";
        try (PreparedStatement statement = transaction.connection().prepareStatement(lookup)) {
            statement.setString(1, messageId);
            statement.setString(2, leaseOwner);
            statement.setTimestamp(3, Timestamp.from(failedAt));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return false;
                }
                int attempts = rows.getInt("attempts");
                MQOutboxStatus status = policy.exhausted(attempts) ? MQOutboxStatus.DEAD : MQOutboxStatus.PENDING;
                Instant availableAt = policy.exhausted(attempts) ? failedAt
                        : policy.nextAvailableAt(attempts, failedAt, ThreadLocalRandom.current().nextDouble());
                String update = "UPDATE sample_order_outbox SET status = ?, available_at = ?, last_error = ?, "
                        + "lease_owner = NULL, lease_until = NULL WHERE id = ?";
                try (PreparedStatement updateStatement = transaction.connection().prepareStatement(update)) {
                    updateStatement.setString(1, status.name());
                    updateStatement.setTimestamp(2, Timestamp.from(availableAt));
                    updateStatement.setString(3, lastError);
                    updateStatement.setString(4, messageId);
                    return updateStatement.executeUpdate() == 1;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to reschedule reliable outbox message", exception);
        }
    }

    private boolean updateWithShortTransaction(SqlOperation operation) {
        AtomicReference<Boolean> updated = new AtomicReference<>();
        transaction.execute(() -> updated.set(operation.execute()));
        return Boolean.TRUE.equals(updated.get());
    }

    private Instant toInstant(ResultSet rows, String column) throws SQLException {
        Timestamp timestamp = rows.getTimestamp(column);
        return Objects.nonNull(timestamp) ? timestamp.toInstant() : null;
    }

    @FunctionalInterface
    private interface SqlOperation {
        boolean execute();
    }
}
