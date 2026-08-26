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
package io.ddd4j.data.event.store.r2dbc;

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.kit.lang.JsonKit;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 R2DBC 的 {@link EventStore} 实现（CQRS 写侧持久化）。
 *
 * <p>通过 {@link ConnectionFactory} 提供响应式数据库连接，内部使用 Reactor
 * 组织读写操作。<b>公共方法边界 {@code block()}</b>：当前 3.0.x EventStore SPI
 * 为同步契约，本实现以 {@code block()} 桥接响应式连接与同步调用方。
 * 原生响应式 API 为后续演进方向（参见 2.0.x {@code AsyncEventStore} SPI）。
 *
 * <h3>表契约</h3>
 * <p>目标表 {@code DDD4J_EVENT_STORE} 与 JPA 模块同构，列定义：
 * {@code aggregate_id} VARCHAR(255)、{@code version} BIGINT、{@code position} BIGINT
 * （UNIQUE）、{@code event_type} VARCHAR(512)、{@code event_id} VARCHAR(64)、
 * {@code payload} CLOB/VARCHAR（JSON 文本）、{@code timestamp} TIMESTAMP。
 * 主键 {@code (aggregate_id, version)}，{@code position} 唯一索引。
 * 表在首次操作时通过 {@code CREATE TABLE IF NOT EXISTS} 懒创建。
 *
     * <h3>乐观锁与 position 生成</h3>
     * <p>append 在事务内以 {@code SELECT COUNT(*)} 读取当前事件数
     * （即当前版本，与 JPA 侧 {@code findCurrentVersion} 语义一致），
     * 与 {@code expectedVersion} 不一致即抛 {@link IllegalStateException} 并回滚。
     * 全局 position 在事务内逐条以 {@code COALESCE(MAX(position), 0) + 1} 生成，
     * 与 JPA 侧策略一致；多实例高并发场景建议切换为数据库序列。
 *
 * <h3>payload 序列化</h3>
 * <p>事件载荷通过 {@link JsonKit#toJson} 序列化为 JSON 文本存储，
 * 读取时通过 {@link JsonKit#toObject} 按 {@code event_type} 反序列化。
 * 若事件类已被删除或重命名（{@code Class.forName} 失败），回退为 {@code Map}，
 * 此时丢失类型信息，javadoc 已说明此限制。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class R2dbcEventStore implements EventStore {

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
                    + "PRIMARY KEY (" + EventStoreConstants.COLUMN_AGGREGATE_ID + ", " + EventStoreConstants.COLUMN_VERSION + "), "
                    + "CONSTRAINT uk_position UNIQUE (" + EventStoreConstants.COLUMN_POSITION + ")"
                    + ")";

    private static final String INSERT_SQL =
            "INSERT INTO " + EventStoreConstants.TABLE_NAME
                    + " (" + EventStoreConstants.COLUMN_AGGREGATE_ID + ", " + EventStoreConstants.COLUMN_VERSION
                    + ", " + EventStoreConstants.COLUMN_POSITION + ", " + EventStoreConstants.COLUMN_EVENT_TYPE
                    + ", " + EventStoreConstants.COLUMN_PAYLOAD + ", " + EventStoreConstants.COLUMN_TIMESTAMP + ")"
                    + " VALUES ($1, $2, $3, $4, $5, $6)";

    private static final String CURRENT_VERSION_SQL =
            "SELECT COUNT(*) FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $1";

    private static final String NEXT_POSITION_SQL =
            "SELECT COALESCE(MAX(" + EventStoreConstants.COLUMN_POSITION + "), 0) FROM " + EventStoreConstants.TABLE_NAME;

    private static final String READ_BY_AGGREGATE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $1 ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_ALL_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_POSITION + " >= $1 ORDER BY " + EventStoreConstants.COLUMN_POSITION + " ASC LIMIT $2";

    private final ConnectionFactory connectionFactory;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 创建 R2DBC 事件存储。
     *
     * @param connectionFactory R2DBC 连接工厂（集成方装配，可为 r2dbc-pool 池化）
     * @throws NullPointerException connectionFactory 为 null 时抛出
     */
    public R2dbcEventStore(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步 SPI 桥接 R2DBC：公共方法边界 {@code block()}。
     * 在非响应式调用方使用时阻塞当前线程；原生响应式 API 为后续演进方向。
     *
     * <p>乐观锁：事务内先查询当前流最大版本，与 {@code expectedVersion} 不一致即抛
     * {@link IllegalStateException} 并回滚。多事件在同一事务内原子提交，
     * 不留半截流。
     */
    @Override
    public void append(String aggregateId, List<Object> events, long expectedVersion) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            return;
        }
        ensureInitialized();
        Connection connection = Mono.from(connectionFactory.create()).block();
        try {
            Mono.from(connection.beginTransaction()).block();

            // 版本校验在事务内执行，与并发写入互斥
            Long actualVersion = Mono.from(connection.createStatement(CURRENT_VERSION_SQL)
                            .bind(0, aggregateId)
                            .execute())
                    .flatMap(result -> Mono.from(result.map((row, meta) -> row.get(0, Long.class))))
                    .block();
            if (actualVersion == null) {
                actualVersion = 0L;
            }
            if (actualVersion != expectedVersion) {
                throw new IllegalStateException(
                        "Version conflict: expected " + expectedVersion + " but was " + actualVersion);
            }

            // position 生成在事务内执行，避免并发连接读到相同的 maxPos
            Long maxPos = Mono.from(connection.createStatement(NEXT_POSITION_SQL).execute())
                    .flatMap(result -> Mono.from(result.map((row, meta) -> row.get(0, Long.class))))
                    .block();
            long position = (maxPos != null ? maxPos : 0L) + 1L;
            LocalDateTime now = LocalDateTime.now();
            long version = expectedVersion;
            for (Object event : events) {
                Statement stmt = connection.createStatement(INSERT_SQL)
                        .bind(0, aggregateId)
                        .bind(1, version)
                        .bind(2, position)
                        .bind(3, event.getClass().getName())
                        .bind(4, JsonKit.toJson(event))
                        .bind(5, now);
                Mono.from(stmt.execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .block();
                version++;
                position++;
            }
            Mono.from(connection.commitTransaction()).block();
        } catch (RuntimeException e) {
            try {
                Mono.from(connection.rollbackTransaction()).block();
            } catch (Exception ignored) {
                // 容忍 rollback 失败，不吞原始异常
            }
            throw e;
        } finally {
            Mono.from(connection.close()).block();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步 SPI 桥接 R2DBC：按版本升序读取指定聚合的全部事件。
     * 事件载荷通过 {@link JsonKit} 反序列化，类型无法还原时回退为 {@code Map}。
     */
    @Override
    public List<StoredEvent> read(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        ensureInitialized();
        return Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(connection.createStatement(READ_BY_AGGREGATE_SQL)
                                .bind(0, aggregateId)
                                .execute())
                        .flatMap(result -> result.map((row, metadata) -> toStoredEvent(row))),
                connection -> Mono.from(connection.close()),
                (connection, ex) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close())
        ).collectList().block();
    }

    /**
     * {@inheritDoc}
     *
     * <p>同步 SPI 桥接 R2DBC：按全局 position 升序分页读取事件。
     * {@code limit} 下推为 SQL {@code LIMIT}，从 SQL 层截断避免物化多余行。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        ensureInitialized();
        return Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(connection.createStatement(READ_ALL_SQL)
                                .bind(0, fromPosition)
                                .bind(1, limit)
                                .execute())
                        .flatMap(result -> result.map((row, metadata) -> toStoredEvent(row))),
                connection -> Mono.from(connection.close()),
                (connection, ex) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close())
        ).collectList().block();
    }

    /**
     * 确保表已创建（懒初始化，仅执行一次）。
     */
    private void ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            Connection connection = Mono.from(connectionFactory.create()).block();
            try {
                Mono.from(connection.createStatement(CREATE_TABLE_SQL).execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .block();
            } finally {
                Mono.from(connection.close()).block();
            }
        }
    }

    /**
     * 将结果集行重建为 {@link StoredEvent}。
     *
     * <p>事件类型经 {@code Class.forName} 还原，payload 经 {@link JsonKit#toObject}
     * 反序列化。若类不存在（被删除/重命名），回退为 {@link JsonKit#toMap}。
     *
 * <p>时间戳处理：r2dbc-h2 对 TIMESTAMP 列返回 {@link LocalDateTime}，
 * 此处统一先取 {@code LocalDateTime} 再转 {@link Instant}（UTC 偏移）。
     *
     * @param row 当前行
     * @return 重建的存储事件
     */
    private StoredEvent toStoredEvent(Row row) {
        String payload = row.get(EventStoreConstants.COLUMN_PAYLOAD, String.class);
        String eventType = row.get(EventStoreConstants.COLUMN_EVENT_TYPE, String.class);
        Object event = deserializePayload(payload, eventType);
        // r2dbc-h2 对 TIMESTAMP 列返回 LocalDateTime，转为 Instant
        Instant timestamp = null;
        LocalDateTime ldt = row.get(EventStoreConstants.COLUMN_TIMESTAMP, LocalDateTime.class);
        if (ldt != null) {
            timestamp = ldt.toInstant(ZoneOffset.UTC);
        } else {
            // 兜底：尝试其他类型（不同 R2DBC 驱动可能返回不同类型）
            try {
                timestamp = row.get(EventStoreConstants.COLUMN_TIMESTAMP, Instant.class);
            } catch (Exception ignored) {
                timestamp = Instant.now();
            }
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        return new StoredEvent(
                row.get(EventStoreConstants.COLUMN_AGGREGATE_ID, String.class),
                row.get(EventStoreConstants.COLUMN_VERSION, Long.class),
                event,
                row.get(EventStoreConstants.COLUMN_POSITION, Long.class),
                timestamp);
    }

    /**
     * 反序列化事件载荷。
     *
     * <p>优先尝试按 {@code eventType} 还原为强类型对象；
     * 若类不存在，回退为 {@code Map}（丢失类型信息）。
     *
     * @param payload   JSON 文本
     * @param eventType 事件类型全限定名
     * @return 反序列化后的事件对象或 Map
     */
    private Object deserializePayload(String payload, String eventType) {
        return io.ddd4j.core.cqrs.eventstore.EventDeserializer.deserialize(payload, eventType);
    }
}
