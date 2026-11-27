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
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
@Slf4j
public class GuiceJdbcProjectionPositionRepository implements ProjectionPositionRepository {

    /**
     * 统一表名，与 Spring/Quarkus 运行时一致
     */
    private static final String TABLE_NAME = ProjectionConstants.TABLE_NAME;

    /** \u66f4\u65b0\u5df2\u6709\u6295\u5f71\u6d41\u68c0\u67e5\u70b9\u7684 SQL\u3002 */
    private static final String UPSERT_UPDATE =
            "UPDATE " + TABLE_NAME + " SET " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " = ? WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    /** \u63d2\u5165\u5c1a\u4e0d\u5b58\u5728\u7684\u6295\u5f71\u6d41\u68c0\u67e5\u70b9\u7684 SQL\u3002 */
    private static final String UPSERT_INSERT =
            "INSERT INTO " + TABLE_NAME + " (" + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + ") VALUES (?, ?)";

    /** \u6839\u636e\u4e8b\u4ef6\u6d41\u6807\u8bc6\u8bfb\u53d6\u4e00\u4e2a\u68c0\u67e5\u70b9\u7684 SQL\u3002 */
    private static final String SELECT_BY_STREAM_ID =
            "SELECT " + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " FROM " + TABLE_NAME + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    /** \u8bfb\u53d6\u6240\u6709\u5df2\u767b\u8bb0\u6295\u5f71\u4f4d\u70b9\u7684 SQL\u3002 */
    private static final String SELECT_ALL =
            "SELECT " + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " FROM " + TABLE_NAME;

    /** \u5220\u9664\u4e00\u4e2a\u4e8b\u4ef6\u6d41\u4f4d\u70b9\u7684 SQL\u3002 */
    private static final String DELETE_BY_STREAM_ID =
            "DELETE FROM " + TABLE_NAME + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    /** \u786e\u4fdd\u5f53\u524d\u6570\u636e\u5e93\u5b58\u5728\u6295\u5f71\u68c0\u67e5\u70b9\u8868\u7684 SQL\u3002 */
    private static final String CREATE_TABLE_IF_NOT_EXISTS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME
                    + " (" + ProjectionConstants.COLUMN_STREAM_ID + " VARCHAR(255) PRIMARY KEY, " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " BIGINT NOT NULL DEFAULT 0)";

    /** \u6267\u884c\u6295\u5f71\u4f4d\u70b9 SQL \u7684 JDBC \u6570\u636e\u6e90\u3002 */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
     * \u5224\u65ad\u662f\u5426\u4e3a\u91cd\u590d\u4e3b\u952e\u5f02\u5e38\uff08\u8de8\u6570\u636e\u5e93\u53ef\u79fb\u690d\uff09\u3002
     *  <p>
     *  H2: SQL state 23505\uff08Unique index or primary key violation\uff09
     *  PostgreSQL: SQL state 23505
     *  MySQL: error code 1062
     *
     * @param e \u6570\u636e\u5e93\u5199\u5165\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u5f02\u5e38\u8868\u793a\u552f\u4e00\u952e\u51b2\u7a81\u65f6\u8fd4\u56de true
     */
    private boolean isDuplicateKeyException(SQLException e) {
        return "23505".equals(e.getSQLState()) || e.getErrorCode() == 1062;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
     * \u4ece ResultSet \u8bfb\u53d6\u4e00\u884c\u6295\u5f71\u4f4d\u7f6e\u3002
     *
     * @param rs \u5305\u542b\u6295\u5f71\u4f4d\u70b9\u67e5\u8be2\u7ed3\u679c\u7684 JDBC \u7ed3\u679c\u96c6
     * @return \u6839\u636e\u7ed3\u679c\u884c\u6062\u590d\u7684\u6295\u5f71\u4f4d\u70b9
     * @throws SQLException \u4ece\u6570\u636e\u5e93\u7ed3\u679c\u884c\u8bfb\u53d6\u6295\u5f71\u4f4d\u70b9\u5931\u8d25
     */
    private ProjectionPosition readPosition(ResultSet rs) throws SQLException {
        return new DefaultProjectionPosition(rs.getString(ProjectionConstants.COLUMN_STREAM_ID), rs.getLong(ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER));
    }
}
