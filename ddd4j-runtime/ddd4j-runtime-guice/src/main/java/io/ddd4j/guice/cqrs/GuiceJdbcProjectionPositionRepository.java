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
package io.ddd4j.guice.cqrs;

import io.ddd4j.core.constant.ProjectionConstants;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 JDBC 的投影位置持久化仓储（纯 java.sql，无 ORM 依赖）。
 *
 * <p>表结构与 Spring/Quarkus 运行时保持一致：
 * <pre>
 * CREATE TABLE DDD4J_PROJECTION_POSITION (
 *     stream_id         VARCHAR(255) PRIMARY KEY,
 *     next_event_number BIGINT NOT NULL DEFAULT 0
 * )
 * </pre>
 *
 * <p>upsert 策略采用「先 UPDATE，影响行数为 0 再 INSERT」的两语句模式，
 * 兼容 H2、PostgreSQL、MySQL 等主流数据库，不依赖特定方言。
 *
 * <p>线程安全说明：并发 update 同一 streamId 时，最后写者胜（last-writer-wins），
 * 符合投影位置的幂等语义——重复处理同一事件号不会产生副作用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public class GuiceJdbcProjectionPositionRepository implements ProjectionPositionRepository {

    private static final Logger log = LoggerFactory.getLogger(GuiceJdbcProjectionPositionRepository.class);

    /** 统一表名，与 Spring/Quarkus 运行时一致 */
    private static final String TABLE_NAME = ProjectionConstants.TABLE_NAME;

    private static final String UPSERT_UPDATE =
            "UPDATE " + TABLE_NAME + " SET " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " = ? WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    private static final String UPSERT_INSERT =
            "INSERT INTO " + TABLE_NAME + " (" + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + ") VALUES (?, ?)";

    private static final String SELECT_BY_STREAM_ID =
            "SELECT " + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " FROM " + TABLE_NAME + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    private static final String SELECT_ALL =
            "SELECT " + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " FROM " + TABLE_NAME;

    private static final String DELETE_BY_STREAM_ID =
            "DELETE FROM " + TABLE_NAME + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    private static final String CREATE_TABLE_IF_NOT_EXISTS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME
                    + " (" + ProjectionConstants.COLUMN_STREAM_ID + " VARCHAR(255) PRIMARY KEY, " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " BIGINT NOT NULL DEFAULT 0)";

    private final DataSource dataSource;

    /**
     * 构造注入数据源，并自动建表（CREATE TABLE IF NOT EXISTS）。
     *
     * @param dataSource JDBC 数据源
     */
    public GuiceJdbcProjectionPositionRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        ensureTable();
    }

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_STREAM_ID)) {
            ps.setString(1, streamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readPosition(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find projection position by streamId: " + streamId, e);
        }
    }

    @Override
    public List<ProjectionPosition> findAll() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            List<ProjectionPosition> result = new ArrayList<>();
            while (rs.next()) {
                result.add(readPosition(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all projection positions", e);
        }
    }

    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        String streamId = position.getStreamId();
        long nextEventNumber = position.getNextEventNumber();

        try (Connection conn = dataSource.getConnection()) {
            // 先尝试 UPDATE
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_UPDATE)) {
                ps.setLong(1, nextEventNumber);
                ps.setString(2, streamId);
                int affected = ps.executeUpdate();
                if (affected > 0) {
                    log.debug("Updated projection position: streamId={}, nextEventNumber={}", streamId, nextEventNumber);
                    return position;
                }
            }
            // UPDATE 影响行数为 0，执行 INSERT
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_INSERT)) {
                ps.setString(1, streamId);
                ps.setLong(2, nextEventNumber);
                ps.executeUpdate();
                log.debug("Inserted projection position: streamId={}, nextEventNumber={}", streamId, nextEventNumber);
            }
            return position;
        } catch (SQLException e) {
            // 并发场景：另一个线程在 UPDATE 和 INSERT 之间插入了同 PK 行 → 重试 UPDATE
            if (isDuplicateKeyException(e)) {
                log.debug("Duplicate key on INSERT, retrying UPDATE: streamId={}", streamId);
                return save(position);
            }
            throw new RuntimeException("Failed to save projection position: streamId=" + streamId, e);
        }
    }

    /**
     * 判断是否为重复主键异常（跨数据库可移植）。
     * <p>
     * H2: SQL state 23505（Unique index or primary key violation）
     * PostgreSQL: SQL state 23505
     * MySQL: error code 1062
     */
    private boolean isDuplicateKeyException(SQLException e) {
        return "23505".equals(e.getSQLState()) || e.getErrorCode() == 1062;
    }

    @Override
    public void deleteByStreamId(String streamId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_STREAM_ID)) {
            ps.setString(1, streamId);
            ps.executeUpdate();
            log.debug("Deleted projection position: streamId={}", streamId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete projection position: streamId=" + streamId, e);
        }
    }

    @Override
    public void resetToZero(String streamId) {
        save(DefaultProjectionPosition.zero(streamId));
    }

    /**
     * 确保投影位置表存在（CREATE TABLE IF NOT EXISTS）。
     */
    private void ensureTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_IF_NOT_EXISTS);
            log.info("Ensured projection position table exists: {}", TABLE_NAME);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure projection position table: " + TABLE_NAME, e);
        }
    }

    /**
     * 从 ResultSet 读取一行投影位置。
     */
    private ProjectionPosition readPosition(ResultSet rs) throws SQLException {
        return new DefaultProjectionPosition(rs.getString(ProjectionConstants.COLUMN_STREAM_ID), rs.getLong(ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER));
    }
}
