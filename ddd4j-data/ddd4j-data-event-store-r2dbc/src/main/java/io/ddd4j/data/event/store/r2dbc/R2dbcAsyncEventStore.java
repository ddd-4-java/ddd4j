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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.AsyncEventStore;
import io.ddd4j.core.cqrs.eventstore.AsyncStoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.*;
import io.ddd4j.kit.text.StrPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * <p><b>与同步轨道的关系</b></p>
 * <ul>
 *   <li>共享同一张表 {@code DDD4J_EVENT_STORE}（建表语句与同步实现一致，含
 *       {@code aggregate_type} 列）；position 全局递增，跨两轨单调不冲突</li>
 *   <li>同步 {@code R2dbcEventStore} 是本实现的阻塞适配器，两种调用方式共享
 *       {@code aggregateId.asTypedString()} 编码与同一套持久化逻辑</li>
 *   <li>读取时经 {@link EntityIdRegistry} 还原 typed id：自定义 id 类型需先
 *       {@code EntityIdRegistry.register(...)}（{@code StringEntityId} 已默认注册）；
 *       未注册类型在 {@link #readAll(long, int)} 中显式报错而非静默降级</li>
 * </ul>
 *
 * <p><b>payload</b></p>
 * <p>经 {@link EventPayloadSerializer}（Jackson 2，无多态标记的安全序列化契约——
 * 2.0.x 主线版本；3.0.x 已迁 Jackson 3，签名相同实现切换）序列化/还原；事件类缺失
 * （被删除/重命名）时以明确错误信号失败——异步轨道的
 * {@link AsyncStoredEvent#payload()} 要求类型化 {@link DomainEvent}，不做 Map 回退。
 *
 * <p><b>时间戳</b></p>
 * <p>写入 {@code LocalDateTime.now()}，读取以系统时区还原 {@link ZonedDateTime}
 * （instant 语义不变）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class R2dbcAsyncEventStore implements AsyncEventStore {

    /**
     * 首次使用时创建事件表的数据库语句。
     */
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

    /**
     * 追加一个领域事件持久化行的语句。
     */
    private static final String INSERT_SQL =
            "INSERT INTO " + EventStoreConstants.TABLE_NAME
                    + " (" + EventStoreConstants.COLUMN_AGGREGATE_ID + ", " + EventStoreConstants.COLUMN_AGGREGATE_TYPE
                    + ", " + EventStoreConstants.COLUMN_VERSION + ", " + EventStoreConstants.COLUMN_POSITION
                    + ", " + EventStoreConstants.COLUMN_EVENT_TYPE + ", " + EventStoreConstants.COLUMN_EVENT_ID
                    + ", " + EventStoreConstants.COLUMN_CORRELATION_ID + ", " + EventStoreConstants.COLUMN_CAUSATION_ID
                    + ", " + EventStoreConstants.COLUMN_PAYLOAD + ", " + EventStoreConstants.COLUMN_TIMESTAMP + ")"
                    + " VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)";

    /**
     * 查询当前聚合已持久化版本的语句。
     */
    private static final String CURRENT_VERSION_SQL =
            "SELECT COUNT(*) FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2";

    /**
     * 会话中读取下一个全局事件位置的语句。
     */
    private static final String NEXT_POSITION_SQL =
            "SELECT COALESCE(MAX(" + EventStoreConstants.COLUMN_POSITION + "), 0) FROM " + EventStoreConstants.TABLE_NAME;

    /**
     * 按聚合标识及版本范围读取事件流的语句。
     */
    private static final String READ_BY_AGGREGATE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    /**
     * 按聚合标识和版本范围读取事件的响应式查询语句。
     */
    private static final String READ_RANGE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2"
                    + " AND " + EventStoreConstants.COLUMN_VERSION + " >= $3"
                    + " AND " + EventStoreConstants.COLUMN_VERSION + " <= $4"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    /**
     * 按全局位置批量读取事件的语句。
     */
    private static final String READ_ALL_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_POSITION + " >= $1"
                    + " ORDER BY " + EventStoreConstants.COLUMN_POSITION + " ASC LIMIT $2";

    /**
     * 创建事件存储 R2DBC 连接的工厂。
     */
    private final ConnectionFactory connectionFactory;

    /**
     * 编码及恢复领域事件负载的序列化器。
     */
    private final EventPayloadSerializer payloadSerializer;

    /**
     * 存储初始化及其共享完成状态。
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 创建异步事件存储（默认发现并注册 Jackson 模块以支持 {@code java.time}）。
     *
     * @param connectionFactory R2DBC 连接工厂（集成方装配，可为 r2dbc-pool 池化）
     */
    public R2dbcAsyncEventStore(ConnectionFactory connectionFactory) {
        this(connectionFactory, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
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
     * 向指定聚合的事件流追加这一批领域事件。
     *
     * <p>\u4e50\u89c2\u9501\u6821\u9a8c\u3001position \u5206\u914d\u4e0e\u5168\u90e8\u4e8b\u4ef6\u5199\u5165\u5728\u540c\u4e00\u4e8b\u52a1\u5185\u539f\u5b50\u5b8c\u6210\uff1b
     * \u7248\u672c\u51b2\u7a81\u4ee5 {@link AggregateVersionConflictException} \u9519\u8bef\u4fe1\u53f7\u7ec8\u6b62\u5e76\u6574\u4f53\u56de\u6eda\u3002
     * \u8ba2\u9605\u5728\u63d0\u4ea4\u524d\u53d6\u6d88\u65f6\uff0c\u56de\u6eda\u4e8b\u52a1\u540e\u518d\u91ca\u653e\u8fde\u63a5\u3002
     *
     * <p>R2DBC 实现调用 <code>events.collectList()</code>。</p>
     *
     * @param aggregateType   聚合类型
     * @param aggregateId     聚合 ID
     * @param events          要追加的事件流（实现会先行物化，订阅一次即完成追加）
     * @param expectedVersion 期望的当前版本号（乐观锁，空流为 0）
     * @return 完成信号；版本冲突时以 {@code AggregateVersionConflictException} 失败
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

    /**
     * 开启事务校验预期版本，追加该聚合的完整事件批次。
     *
     * @param aggregateType   聚合类型名称
     * @param aggregateId     聚合根标识
     * @param events          这一批次要追加到聚合流的事件
     * @param expectedVersion 本次追加期望匹配的聚合当前版本
     * @return 追加提交或失败时结束的响应式信号
     */
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
                (connection, error) -> rollbackAndClose(connection),
                this::rollbackAndClose));
    }

    /**
     * 尝试回滚事务，再关闭响应式数据库连接。
     *
     * @param connection 运行当前响应式 SQL 的数据库连接
     * @return 回滚及连接清理结束时完成的响应式信号
     */
    private Mono<Void> rollbackAndClose(Connection connection) {
        return Mono.defer(() -> Mono.from(connection.rollbackTransaction()))
                .onErrorResume(ignored -> Mono.empty())
                .then(Mono.defer(() -> Mono.from(connection.close())));
    }

    /**
     * 按聚合查询其事件流并还原已存储的事件。
     *
     * <p>R2DBC 实现调用 <code>ensureInitialized()</code>。</p>
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @return 按版本升序的持久化事件流；无事件时为空流
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
     * 按聚合查询其事件流并还原已存储的事件。
     *
     * <p>R2DBC 实现调用 <code>ensureInitialized()</code>。</p>
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param fromVersion   起始版本号（含）
     * @param toVersion     结束版本号（含）
     * @return 版本区间内的持久化事件流，按版本升序
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
     * 按全局位置读取用于投影或重放的存储事件。
     *
     * <p>typed aggregate id \u7ecf {@link EntityIdRegistry} \u8fd8\u539f\uff1a\u81ea\u5b9a\u4e49 id \u7c7b\u578b\u9700\u5148\u6ce8\u518c\uff0c
     * \u672a\u6ce8\u518c\u7c7b\u578b\u663e\u5f0f\u62a5\u9519\uff08\u9644\u6ce8\u518c\u6307\u5f15\uff09\u3002
     *
     * <p>R2DBC 实现调用 <code>Flux.error(new IllegalArgumentException("limit must be positive"))</code>。</p>
     *
     * @param fromPosition 起始 position（含）
     * @param limit        最大读取数量
     * @return 实际返回<code>Flux.error(new IllegalArgumentException("limit must be positive"))</code>
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
     *
     * @return 在数据库初始化完成时结束的响应式信号
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

    /**
     * 在当前数据库连接中查询聚合的已写入版本。
     *
     * @param connection        运行当前响应式 SQL 的数据库连接
     * @param aggregateType     聚合类型名称
     * @param aggregateIdString 事件表使用的字符串聚合标识
     * @return 返回聚合当前版本的响应式结果
     */
    private Mono<Long> currentVersion(Connection connection, String aggregateType, String aggregateIdString) {
        return Mono.from(connection.createStatement(CURRENT_VERSION_SQL)
                        .bind(0, aggregateType)
                        .bind(1, aggregateIdString)
                        .execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get(0, Long.class))))
                .defaultIfEmpty(0L);
    }

    /**
     * 从事件表读取准备分配的下一全局位置。
     *
     * @param connection 运行当前响应式 SQL 的数据库连接
     * @return 发出下一位置的响应式结果
     */
    private Mono<Long> nextPosition(Connection connection) {
        return Mono.from(connection.createStatement(NEXT_POSITION_SQL).execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get(0, Long.class))))
                .defaultIfEmpty(0L);
    }

    /**
     * 顺序写入全部事件：position 在事务内按 {@code MAX+1} 递增分配，
     * 首事件版本为 {@code expectedVersion + 1}；任一写入失败整体回滚。
     *
     * @param connection        当前事务使用的数据库连接
     * @param aggregateType     聚合类型
     * @param aggregateIdString 持久化使用的聚合根标识
     * @param events            按顺序追加或回放的领域事件
     * @param expectedVersion   预期的当前聚合版本
     * @return 在本批事件全部插入时完成的响应式信号
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
                                .bind(9, event.getEventTimestamp()
                                        .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                                .execute())
                        .flatMap(result -> Mono.from(result.getRowsUpdated()))
                        .then());
                version++;
                position++;
            }
            return chain;
        });
    }

    /**
     * 在语句的指定位置绑定字符串或显式的字符串类型 SQL NULL。
     *
     * @param statement 为当前事件写入操作准备的 SQL 语句对象
     * @param index     语句中的参数位置
     * @param value     要绑定的可空字符串
     */
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
                ZonedDateTime.of(timestamp, ZoneOffset.UTC),
                event,
                EventId.valueOf(row.get(EventStoreConstants.COLUMN_CORRELATION_ID, String.class)),
                EventId.valueOf(row.get(EventStoreConstants.COLUMN_CAUSATION_ID, String.class)));
    }

    /**
     * 按事件类型名解析 Java 类并反序列化事件负载。
     *
     * @param payload   数据库中保存的已序列化事件负载
     * @param eventType 事件的 Java 类型或持久化类型名称
     * @return 恢复出的领域事件
     */
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
