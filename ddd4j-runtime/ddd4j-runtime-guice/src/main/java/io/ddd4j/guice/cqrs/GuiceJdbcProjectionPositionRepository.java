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

    /**
     * 更新已有投影流检查点的 SQL。
     */
    private static final String UPSERT_UPDATE =
            "UPDATE " + TABLE_NAME + " SET " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " = ? WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    /**
     * 插入尚不存在的投影流检查点的 SQL。
     */
    private static final String UPSERT_INSERT =
            "INSERT INTO " + TABLE_NAME + " (" + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + ") VALUES (?, ?)";

    /**
     * 根据事件流标识读取一个检查点的 SQL。
     */
    private static final String SELECT_BY_STREAM_ID =
            "SELECT " + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " FROM " + TABLE_NAME + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    /**
     * 读取所有已登记投影位点的 SQL。
     */
    private static final String SELECT_ALL =
            "SELECT " + ProjectionConstants.COLUMN_STREAM_ID + ", " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " FROM " + TABLE_NAME;

    /**
     * 删除一个事件流位点的 SQL。
     */
    private static final String DELETE_BY_STREAM_ID =
            "DELETE FROM " + TABLE_NAME + " WHERE " + ProjectionConstants.COLUMN_STREAM_ID + " = ?";

    /**
     * 确保当前数据库存在投影检查点表的 SQL。
     */
    private static final String CREATE_TABLE_IF_NOT_EXISTS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME
                    + " (" + ProjectionConstants.COLUMN_STREAM_ID + " VARCHAR(255) PRIMARY KEY, " + ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER + " BIGINT NOT NULL DEFAULT 0)";

    /**
     * 执行投影位点 SQL 的 JDBC 数据源。
     */
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

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>dataSource.getConnection()</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
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

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>此实现调用 <code>dataSource.getConnection()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
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

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>position.getStreamId()</code>。</p>
     *
     * @param position 投影位置
     * @return 根据执行路径返回<code>position</code>、<code>save(position)</code>
     */
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
     *
     * @param e 数据库写入抛出的异常
     * @return 异常表示唯一键冲突时返回 true
     */
    private boolean isDuplicateKeyException(SQLException e) {
        return "23505".equals(e.getSQLState()) || e.getErrorCode() == 1062;
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>此实现调用 <code>dataSource.getConnection()</code>。</p>
     *
     * @param streamId 投影流 ID
     */
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

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>此实现调用 <code>save(DefaultProjectionPosition.zero(streamId))</code>。</p>
     *
     * @param streamId 投影流 ID
     */
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
     *
     * @param rs 包含投影位点查询结果的 JDBC 结果集
     * @return 根据结果行恢复的投影位点
     * @throws SQLException 从数据库结果行读取投影位点失败
     */
    private ProjectionPosition readPosition(ResultSet rs) throws SQLException {
        return new DefaultProjectionPosition(rs.getString(ProjectionConstants.COLUMN_STREAM_ID), rs.getLong(ProjectionConstants.COLUMN_NEXT_EVENT_NUMBER));
    }
}
