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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.AsyncEventStore;
import io.ddd4j.core.cqrs.eventstore.AsyncStoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdRegistry;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.kit.text.StrPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事件存储异步轨道实现（纯 {@code io.r2dbc.spi} 真响应式事务，ADR-0005 单轨决策）。
 *
 * <p>实现 core 的 {@link AsyncEventStore}：append 的乐观锁校验、position 分配与写入
 * 在同一事务内原子完成（{@code beginTransaction → 校验 → 写入 → commit}，失败整体回滚
 * 不留半截流）；读侧以 {@link Flux} 流式输出，{@code limit} 下推为 SQL 分页。
 *
 * <h3>与同步轨道的关系</h3>
 * <ul>
 *   <li>共享同一张表 {@code DDD4J_EVENT_STORE}（建表语句与同步实现一致，含
 *       {@code aggregate_type} 列）；position 全局递增，跨两轨单调不冲突</li>
 *   <li><b>aggregate_id 编码不同</b>：本实现写入
 *       {@code aggregateId.asTypedString()}（{@code Type:value}），同步轨道写入纯
 *       {@code asString()}——同一聚合请勿混用两条轨道</li>
 *   <li>读取时经 {@link EntityIdRegistry} 还原 typed id：自定义 id 类型需先
 *       {@code EntityIdRegistry.register(...)}（{@code StringEntityId} 已默认注册）；
 *       未注册类型在 {@link #readAll(long, int)} 中显式报错而非静默降级</li>
 * </ul>
 *
 * <h3>payload</h3>
 * <p>经 {@link EventPayloadSerializer}（Jackson 2，无多态标记的安全序列化契约——
 * 2.0.x 主线版本；3.0.x 已迁 Jackson 3，签名相同实现切换）序列化/还原；事件类缺失
 * （被删除/重命名）时以明确错误信号失败——异步轨道的
 * {@link AsyncStoredEvent#payload()} 要求类型化 {@link DomainEvent}，不做 Map 回退。
 *
 * <h3>时间戳</h3>
 * <p>写入 {@code LocalDateTime.now()}，读取以系统时区还原 {@link ZonedDateTime}
 * （instant 语义不变）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class R2dbcAsyncEventStore implements AsyncEventStore {

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + EventStoreConstants.TABLE_NAME + " ("
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + " VARCHAR(255) NOT NULL, "
                    + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " VARCHAR(255) NOT NULL, "
                    + EventStoreConstants.COLUMN_VERSION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_POSITION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_TYPE + " VARCHAR(512) NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_CORRELATION_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_CAUSATION_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_PAYLOAD + " TEXT NOT NULL, "
                    + EventStoreConstants.COLUMN_TIMESTAMP + " TIMESTAMP NOT NULL, "
                    + "PRIMARY KEY (" + EventStoreConstants.COLUMN_AGGREGATE_TYPE + ", "
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + ", " + EventStoreConstants.COLUMN_VERSION + "), "
                    + "CONSTRAINT uk_position UNIQUE (" + EventStoreConstants.COLUMN_POSITION + ")"
                    + ")";

    private static final String INSERT_SQL =
            "INSERT INTO " + EventStoreConstants.TABLE_NAME
                    + " (" + EventStoreConstants.COLUMN_AGGREGATE_ID + ", " + EventStoreConstants.COLUMN_AGGREGATE_TYPE
                    + ", " + EventStoreConstants.COLUMN_VERSION + ", " + EventStoreConstants.COLUMN_POSITION
                    + ", " + EventStoreConstants.COLUMN_EVENT_TYPE + ", " + EventStoreConstants.COLUMN_EVENT_ID
                    + ", " + EventStoreConstants.COLUMN_CORRELATION_ID + ", " + EventStoreConstants.COLUMN_CAUSATION_ID
                    + ", " + EventStoreConstants.COLUMN_PAYLOAD + ", " + EventStoreConstants.COLUMN_TIMESTAMP + ")"
                    + " VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)";

    private static final String CURRENT_VERSION_SQL =
            "SELECT COUNT(*) FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2";

    private static final String NEXT_POSITION_SQL =
            "SELECT COALESCE(MAX(" + EventStoreConstants.COLUMN_POSITION + "), 0) FROM " + EventStoreConstants.TABLE_NAME;

    private static final String READ_BY_AGGREGATE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_RANGE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2"
                    + " AND " + EventStoreConstants.COLUMN_VERSION + " >= $3"
                    + " AND " + EventStoreConstants.COLUMN_VERSION + " <= $4"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_ALL_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_POSITION + " >= $1"
                    + " ORDER BY " + EventStoreConstants.COLUMN_POSITION + " ASC LIMIT $2";

    private final ConnectionFactory connectionFactory;

    private final EventPayloadSerializer payloadSerializer;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 创建异步事件存储（默认使用 {@code new ObjectMapper()} 的 payload 序列化器）。
     *
     * @param connectionFactory R2DBC 连接工厂（集成方装配，可为 r2dbc-pool 池化）
     */
    public R2dbcAsyncEventStore(ConnectionFactory connectionFactory) {
        this(connectionFactory, new EventPayloadSerializer(new ObjectMapper()));
    }

    /**
     * 创建异步事件存储。
     *
     * @param connectionFactory R2DBC 连接工厂
     * @param payloadSerializer payload 序列化器（Jackson 2 mapper 的副本隔离语义见其构造器）
     */
    public R2dbcAsyncEventStore(ConnectionFactory connectionFactory, EventPayloadSerializer payloadSerializer) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
        this.payloadSerializer = Objects.requireNonNull(payloadSerializer, "payloadSerializer must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁校验、position 分配与全部事件写入在同一事务内原子完成；
     * 版本冲突以 {@link AggregateVersionConflictException} 错误信号终止并整体回滚。
     */
    @Override
    public Mono<Void> append(String aggregateType, AggregateRootId aggregateId,
                             Flux<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        return events.collectList()
                .flatMap(list -> list.isEmpty()
                        ? Mono.empty()
                        : doAppend(aggregateType, aggregateId, list, expectedVersion));
    }

    private Mono<Void> doAppend(String aggregateType, AggregateRootId aggregateId,
                                List<? extends DomainEvent<?>> events, long expectedVersion) {
        String aggregateIdString = aggregateId.asTypedString();
        return ensureInitialized().then(Mono.usingWhen(
                connectionFactory.create(),
                connection -> Mono.from(connection.beginTransaction())
                        .then(currentVersion(connection, aggregateType, aggregateIdString))
                        .flatMap(actualVersion -> {
                            if (actualVersion != expectedVersion) {
                                return Mono.error(new AggregateVersionConflictException(
                                        aggregateType, aggregateId.asString(), expectedVersion, actualVersion));
                            }
                            return insertAll(connection, aggregateType, aggregateIdString, events, expectedVersion);
                        })
                        .then(Mono.from(connection.commitTransaction())),
                connection -> Mono.from(connection.close()),
                (connection, error) -> Mono.from(connection.rollbackTransaction())
                        .onErrorResume(ignored -> Mono.empty())
                        .then(Mono.from(connection.close())),
                connection -> Mono.from(connection.close())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<AsyncStoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return ensureInitialized().thenMany(Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(connection.createStatement(READ_BY_AGGREGATE_SQL)
                                .bind(0, aggregateType)
                                .bind(1, aggregateId.asTypedString())
                                .execute())
                        .flatMap(result -> result.map((row, metadata) -> toAsyncStoredEvent(row, aggregateType))),
                connection -> Mono.from(connection.close()),
                (connection, error) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<AsyncStoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                       long fromVersion, long toVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return ensureInitialized().thenMany(Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(connection.createStatement(READ_RANGE_SQL)
                                .bind(0, aggregateType)
                                .bind(1, aggregateId.asTypedString())
                                .bind(2, fromVersion)
                                .bind(3, toVersion)
                                .execute())
                        .flatMap(result -> result.map((row, metadata) -> toAsyncStoredEvent(row, aggregateType))),
                connection -> Mono.from(connection.close()),
                (connection, error) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close())));
    }

    /**
     * {@inheritDoc}
     *
     * <p>typed aggregate id 经 {@link EntityIdRegistry} 还原：自定义 id 类型需先注册，
     * 未注册类型显式报错（附注册指引）。
     */
    @Override
    public Flux<AsyncStoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) {
            return Flux.error(new IllegalArgumentException("limit must be positive"));
        }
        return ensureInitialized().thenMany(Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(connection.createStatement(READ_ALL_SQL)
                                .bind(0, fromPosition)
                                .bind(1, limit)
                                .execute())
                        .flatMap(result -> result.map((row, metadata) -> toAsyncStoredEvent(row, null))),
                connection -> Mono.from(connection.close()),
                (connection, error) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close())));
    }

    /**
     * 确保表已创建（懒初始化，幂等；并发下重复执行 {@code IF NOT EXISTS} 无害）。
     */
    private Mono<Void> ensureInitialized() {
        if (initialized.get()) {
            return Mono.empty();
        }
        return Mono.usingWhen(
                connectionFactory.create(),
                connection -> Mono.from(connection.createStatement(CREATE_TABLE_SQL).execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .then(),
                connection -> Mono.from(connection.close()),
                (connection, error) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close()))
                .doOnSuccess(v -> initialized.set(true));
    }

    private Mono<Long> currentVersion(Connection connection, String aggregateType, String aggregateIdString) {
        return Mono.from(connection.createStatement(CURRENT_VERSION_SQL)
                        .bind(0, aggregateType)
                        .bind(1, aggregateIdString)
                        .execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get(0, Long.class))))
                .defaultIfEmpty(0L);
    }

    private Mono<Long> nextPosition(Connection connection) {
        return Mono.from(connection.createStatement(NEXT_POSITION_SQL).execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get(0, Long.class))))
                .defaultIfEmpty(0L);
    }

    /**
     * 顺序写入全部事件：position 在事务内按 {@code MAX+1} 递增分配，
     * 首事件版本为 {@code expectedVersion + 1}；任一写入失败整体回滚。
     */
    private Mono<Void> insertAll(Connection connection, String aggregateType, String aggregateIdString,
                                 List<? extends DomainEvent<?>> events, long expectedVersion) {
        return nextPosition(connection).flatMap(maxPosition -> {
            long position = maxPosition + 1;
            long version = expectedVersion + 1L;
            Mono<Void> chain = Mono.empty();
            for (DomainEvent<?> event : events) {
                final long eventVersion = version;
                final long eventPosition = position;
                Statement statement = connection.createStatement(INSERT_SQL)
                        .bind(0, aggregateIdString)
                        .bind(1, aggregateType)
                        .bind(2, eventVersion)
                        .bind(3, eventPosition)
                        .bind(4, event.getClass().getName())
                        .bind(5, event.getEventId().asString());
                bindNullable(statement, 6, event.getCorrelationId() == null ? null : event.getCorrelationId().asString());
                bindNullable(statement, 7, event.getCausationId() == null ? null : event.getCausationId().asString());
                chain = chain.then(Mono.from(statement
                                .bind(8, payloadSerializer.serialize(event))
                                .bind(9, LocalDateTime.now())
                                .execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .then());
                version++;
                position++;
            }
            return chain;
        });
    }

    private void bindNullable(Statement statement, int index, String value) {
        if (value == null) {
            statement.bindNull(index, String.class);
        } else {
            statement.bind(index, value);
        }
    }

    /**
     * 将结果集行重建为 {@link AsyncStoredEvent}。
     *
     * <p>payload 经 {@link EventPayloadSerializer} 按 {@code event_type} 还原类型化
     * {@link DomainEvent}；correlationId/causationId 从还原的事件元数据提取；
     * typed aggregate id 经 {@link EntityIdRegistry} 还原（未注册类型报错并附指引）。
     *
     * @param row           当前行
     * @param aggregateType 查询参数提供的聚合类型；{@code readAll} 场景为 {@code null}（取列值）
     * @return 重建的异步存储事件
     */
    private AsyncStoredEvent toAsyncStoredEvent(Row row, String aggregateType) {
        String aggregateIdString = row.get(EventStoreConstants.COLUMN_AGGREGATE_ID, String.class);
        String storedAggregateType = row.get(EventStoreConstants.COLUMN_AGGREGATE_TYPE, String.class);
        String resolvedAggregateType = aggregateType != null ? aggregateType : storedAggregateType;
        long version = row.get(EventStoreConstants.COLUMN_VERSION, Long.class);
        long position = row.get(EventStoreConstants.COLUMN_POSITION, Long.class);
        String eventType = row.get(EventStoreConstants.COLUMN_EVENT_TYPE, String.class);
        String payload = row.get(EventStoreConstants.COLUMN_PAYLOAD, String.class);
        String eventId = row.get(EventStoreConstants.COLUMN_EVENT_ID, String.class);
        LocalDateTime timestamp = row.get(EventStoreConstants.COLUMN_TIMESTAMP, LocalDateTime.class);

        DomainEvent<?> event = deserializePayload(payload, eventType);
        return new AsyncStoredEvent(
                eventId != null ? EventId.valueOf(eventId) : event.getEventId(),
                resolvedAggregateType,
                parseAggregateId(aggregateIdString),
                version,
                position,
                ZonedDateTime.of(timestamp, ZoneId.systemDefault()),
                event,
                EventId.valueOf(row.get(EventStoreConstants.COLUMN_CORRELATION_ID, String.class)),
                EventId.valueOf(row.get(EventStoreConstants.COLUMN_CAUSATION_ID, String.class)));
    }

    @SuppressWarnings("unchecked")
    private DomainEvent<?> deserializePayload(String payload, String eventType) {
        try {
            Class<?> eventClass = Class.forName(eventType);
            return payloadSerializer.deserialize(payload, (Class<? extends DomainEvent<?>>) eventClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Event class not found: " + eventType
                    + "（异步轨道要求类型化 DomainEvent，不做 Map 回退）", e);
        }
    }

    /**
     * 从 {@code Type:value} 还原 typed aggregate id。
     *
     * @param typedId typed id 字符串（{@link AggregateRootId#asTypedString()} 格式）
     * @return 还原的聚合根标识
     * @throws IllegalStateException 类型未注册或非聚合根标识类型时抛出（附注册指引）
     */
    private AggregateRootId parseAggregateId(String typedId) {
        int separator = typedId.indexOf(StrPool.COLON);
        if (separator <= 0) {
            throw new IllegalStateException("Invalid typed aggregate id: " + typedId);
        }
        String typeName = typedId.substring(0, separator);
        String value = typedId.substring(separator + 1);
        EntityId id = EntityIdRegistry.valueOf(typeName, value);
        if (id instanceof AggregateRootId aggregateRootId) {
            return aggregateRootId;
        }
        throw new IllegalStateException("Aggregate id type not registered: " + typeName
                + " — 请通过 EntityIdRegistry.register(\"" + typeName + "\", ...) 注册该 id 类型的工厂");
    }
}