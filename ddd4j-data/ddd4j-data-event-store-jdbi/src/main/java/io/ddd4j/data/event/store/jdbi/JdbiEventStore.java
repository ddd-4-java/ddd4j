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
package io.ddd4j.data.event.store.jdbi;

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.EventDeserializer;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.kit.lang.JsonKit;
import org.jdbi.v3.core.Jdbi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 JDBI 的 {@link EventStore} 实现（CQRS 写侧持久化）。
 *
 * <p>SQL-first：全部读写以手写 SQL 经 jdbi3-core 的 Statement/Handle 原语执行
 * （无注解 SQL Object），适用于 Javalin/Vert.x 等轻量运行时；Spring 系运行时请用
 * {@code ddd4j-data-event-store-jpa}，Quarkus 请用 {@code ddd4j-data-event-store-panache}，
 * 响应式请用 {@code ddd4j-data-event-store-r2dbc}。
 *
 * <h3>集成方装配</h3>
 * <p>本类为纯类（零容器注解、非容器托管）：Javalin/Vert.x 集成方在应用装配代码中
 * 手动 {@code new JdbiEventStore(jdbi)} 并自行管理其生命周期——{@code Jdbi}
 * 实例（可包连接池 DataSource）由集成方提供。
 *
 * <h3>表契约</h3>
 * <p>目标表 {@code DDD4J_EVENT_STORE} 与 -jpa/-r2dbc/-esdb 模块同构：
 * {@code aggregate_id} VARCHAR(255)、{@code aggregate_type} VARCHAR(255)（可空）、
 * {@code version} BIGINT、{@code position} BIGINT（UNIQUE）、
 * {@code event_type} VARCHAR(512)、{@code event_id} VARCHAR(64)、
 * {@code payload} CLOB（JSON 文本）、{@code timestamp} TIMESTAMP。
 * 主键 {@code (aggregate_id, version)}，{@code position} 唯一索引。
 * 表在首次操作时通过 {@code CREATE TABLE IF NOT EXISTS} 懒创建。
 *
 * <h3>乐观锁与 position 生成</h3>
 * <p>append 在事务内以 {@code SELECT COUNT(*)} 读取当前事件数
 * （即当前版本），与 {@code expectedVersion} 不一致即抛 {@link IllegalStateException}
 * 并回滚。全局 position 在事务内逐条以 {@code COALESCE(MAX(position), 0) + 1} 生成，
 * 与 JPA/R2DBC 侧策略一致。
 *
 * <h3>payload 序列化</h3>
 * <p>事件载荷通过 {@link JsonKit#toJson} 序列化为 JSON 文本存储，
 * 读取时通过 {@link EventDeserializer#deserialize} 按 {@code event_type} 反序列化。
 * 若事件类已被删除或重命名（{@code Class.forName} 失败），回退为 {@code Map}，
 * 此时丢失类型信息，javadoc 已说明此限制。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class JdbiEventStore implements EventStore {

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + EventStoreConstants.TABLE_NAME + " ("
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + " VARCHAR(255) NOT NULL, "
                    + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " VARCHAR(255), "
                    + EventStoreConstants.COLUMN_VERSION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_POSITION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_TYPE + " VARCHAR(512) NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_PAYLOAD + " CLOB NOT NULL, "
                    + EventStoreConstants.COLUMN_TIMESTAMP + " TIMESTAMP NOT NULL, "
                    + "PRIMARY KEY (" + EventStoreConstants.COLUMN_AGGREGATE_ID + ", "
                    + EventStoreConstants.COLUMN_VERSION + "), "
                    + "CONSTRAINT uk_position UNIQUE (" + EventStoreConstants.COLUMN_POSITION + ")"
                    + ")";

    private static final String CURRENT_VERSION_SQL =
            "SELECT COUNT(*) FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId";

    private static final String NEXT_POSITION_SQL =
            "SELECT COALESCE(MAX(" + EventStoreConstants.COLUMN_POSITION + "), 0) FROM "
                    + EventStoreConstants.TABLE_NAME;

    private static final String INSERT_SQL =
            "INSERT INTO " + EventStoreConstants.TABLE_NAME
                    + " (" + EventStoreConstants.COLUMN_AGGREGATE_ID
                    + ", " + EventStoreConstants.COLUMN_VERSION
                    + ", " + EventStoreConstants.COLUMN_POSITION
                    + ", " + EventStoreConstants.COLUMN_EVENT_TYPE
                    + ", " + EventStoreConstants.COLUMN_PAYLOAD
                    + ", " + EventStoreConstants.COLUMN_TIMESTAMP
                    + ") VALUES (:aggregateId, :version, :position, :eventType, :payload, :timestamp)";

    private static final String READ_BY_AGGREGATE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_ALL_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_POSITION + " >= :fromPosition"
                    + " ORDER BY " + EventStoreConstants.COLUMN_POSITION + " ASC"
                    + " LIMIT :limit";

    private final Jdbi jdbi;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 创建 JDBI 事件存储。
     *
     * @param jdbi JDBI 实例（集成方装配，可包连接池 DataSource）
     * @throws NullPointerException jdbi 为 null 时抛出
     */
    public JdbiEventStore(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁：事务内先查询当前流事件数（即当前版本），与 {@code expectedVersion}
     * 不一致即抛 {@link IllegalStateException} 并回滚。多事件在同一事务内原子提交，
     * 不留半截流。
     *
     * <p>position 生成：事务内逐条以 {@code COALESCE(MAX(position), 0) + 1} 递增，
     * 与 JPA/R2DBC 侧策略一致。首条的 position 在循环前预取 maxPos，
     * 后续每条 +1，避免每条都执行 MAX 查询。
     */
    @Override
    public void append(String aggregateId, List<Object> events, long expectedVersion) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            return;
        }
        ensureInitialized();
        jdbi.useTransaction(handle -> {
            // 版本校验（乐观锁第一道）
            long actualVersion = handle.createQuery(CURRENT_VERSION_SQL)
                    .bind("aggregateId", aggregateId)
                    .mapTo(Long.class)
                    .one();
            if (actualVersion != expectedVersion) {
                throw new IllegalStateException(
                        "Version conflict: expected " + expectedVersion + " but was " + actualVersion);
            }

            // position 生成在事务内执行，避免并发连接读到相同的 maxPos
            long maxPos = handle.createQuery(NEXT_POSITION_SQL)
                    .mapTo(Long.class)
                    .one();
            long position = maxPos + 1L;
            LocalDateTime now = LocalDateTime.now();
            long version = expectedVersion;
            for (Object event : events) {
                handle.createUpdate(INSERT_SQL)
                        .bind("aggregateId", aggregateId)
                        .bind("version", version)
                        .bind("position", position)
                        .bind("eventType", event.getClass().getName())
                        .bind("payload", JsonKit.toJson(event))
                        .bind("timestamp", now)
                        .execute();
                version++;
                position++;
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>按版本升序读取指定聚合的全部事件。
     * 事件载荷通过 {@link EventDeserializer} 反序列化，类型无法还原时回退为 {@code Map}。
     */
    @Override
    public List<StoredEvent> read(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_BY_AGGREGATE_SQL)
                .bind("aggregateId", aggregateId)
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    /**
     * {@inheritDoc}
     *
     * <p>按全局 position 升序分页读取事件。
     * {@code limit} 下推为 SQL {@code LIMIT}，从 SQL 层截断避免物化多余行。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_ALL_SQL)
                .bind("fromPosition", fromPosition)
                .bind("limit", limit)
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    /**
     * 确保表已创建（懒初始化，仅执行一次）。
     */
    private void ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            jdbi.useHandle(handle -> handle.execute(CREATE_TABLE_SQL));
        }
    }

    /**
     * 将结果集行重建为 {@link StoredEvent}。
     *
     * <p>事件类型经 {@code Class.forName} 还原，payload 经 {@link EventDeserializer#deserialize}
     * 反序列化。若类不存在（被删除/重命名），回退为 {@link JsonKit#toMap}。
     *
     * <p>时间戳处理：H2 JDBC 驱动对 TIMESTAMP 列返回 {@link LocalDateTime}，
     * 此处统一先取 {@link LocalDateTime} 再转 {@link Instant}（UTC 偏移），
     * 与 R2DBC 实现一致。
     *
     * @param rs 当前行（列名与表契约一致）
     * @return 重建的存储事件
     * @throws SQLException JDBC 列读取失败
     */
    private StoredEvent toStoredEvent(ResultSet rs) throws SQLException {
        String payload = rs.getString(EventStoreConstants.COLUMN_PAYLOAD);
        String eventType = rs.getString(EventStoreConstants.COLUMN_EVENT_TYPE);
        Object event = EventDeserializer.deserialize(payload, eventType);
        // H2 JDBC 对 TIMESTAMP 列返回 LocalDateTime，转为 Instant（与 R2DBC 实现一致）
        Instant timestamp;
        LocalDateTime ldt = rs.getObject(EventStoreConstants.COLUMN_TIMESTAMP, LocalDateTime.class);
        if (ldt != null) {
            timestamp = ldt.toInstant(ZoneOffset.UTC);
        } else {
            timestamp = Instant.now();
        }
        return new StoredEvent(
                rs.getString(EventStoreConstants.COLUMN_AGGREGATE_ID),
                rs.getLong(EventStoreConstants.COLUMN_VERSION),
                event,
                rs.getLong(EventStoreConstants.COLUMN_POSITION),
                timestamp);
    }
}
