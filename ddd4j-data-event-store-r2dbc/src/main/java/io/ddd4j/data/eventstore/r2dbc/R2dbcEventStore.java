package io.ddd4j.data.eventstore.r2dbc;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.AsyncEventStore;
import io.ddd4j.core.cqrs.eventstore.AsyncStoredEvent;
import io.ddd4j.core.cqrs.eventstore.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.AggregateVersion;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 基于 R2DBC SPI + Reactor 的 {@link AsyncEventStore} 响应式适配器（无框架绑定，JDK8）。
 *
 * <h3>schema</h3>
 * <p>统一表 {@code DDD4J_EVENT_STORE}（与 JPA/JDBI 适配器共用
 * {@link EventStoreConstants}），payload 为 TEXT（与 2.0.x/3.0.x 统一 schema
 * 对齐）。表在首次操作时通过 {@code CREATE TABLE IF NOT EXISTS} 懒创建。
 *
 * <h3>乐观锁双保险</h3>
 * <p>append 在事务内 {@code COUNT} 校验 {@code expectedVersion}，不一致抛
 * {@link AggregateVersionConflictException}；复合主键
 * {@code (aggregate_type, aggregate_id, version)} 与 {@code position} 唯一约束
 * 兜底并发漏检窗口。position 在事务内按 {@code MAX+1} 递增分配。
 *
 * <h3>事务语义</h3>
 * <p>所有操作经 {@code usingWhen} 管理连接生命周期：成功路径提交事务，异常路径
 * 回滚（回滚失败不掩盖原异常），连接在信号终结后关闭。
 *
 * <h3>版本语义</h3>
 * <p>与三分支统一契约一致：空流 {@code expectedVersion=0}，事件版本从
 * {@code expectedVersion + 1} 起分配（1-based）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public class R2dbcEventStore implements AsyncEventStore {

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
                    + "CONSTRAINT uk_" + EventStoreConstants.TABLE_NAME + "_position UNIQUE ("
                    + EventStoreConstants.COLUMN_POSITION + ")"
                    + ")";

    private static final String CURRENT_VERSION_SQL =
            "SELECT COUNT(*) FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2";

    private static final String NEXT_POSITION_SQL =
            "SELECT COALESCE(MAX(" + EventStoreConstants.COLUMN_POSITION + "), 0) FROM "
                    + EventStoreConstants.TABLE_NAME;

    private static final String INSERT_SQL =
            "INSERT INTO " + EventStoreConstants.TABLE_NAME
                    + " (" + EventStoreConstants.COLUMN_AGGREGATE_ID
                    + ", " + EventStoreConstants.COLUMN_AGGREGATE_TYPE
                    + ", " + EventStoreConstants.COLUMN_VERSION
                    + ", " + EventStoreConstants.COLUMN_POSITION
                    + ", " + EventStoreConstants.COLUMN_EVENT_TYPE
                    + ", " + EventStoreConstants.COLUMN_EVENT_ID
                    + ", " + EventStoreConstants.COLUMN_CORRELATION_ID
                    + ", " + EventStoreConstants.COLUMN_CAUSATION_ID
                    + ", " + EventStoreConstants.COLUMN_PAYLOAD
                    + ", " + EventStoreConstants.COLUMN_TIMESTAMP + ")"
                    + " VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)";

    private static final String READ_BY_AGGREGATE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_RANGE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = $1"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = $2"
                    + " AND " + EventStoreConstants.COLUMN_VERSION + " BETWEEN $3 AND $4"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_ALL_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_POSITION + " >= $1"
                    + " ORDER BY " + EventStoreConstants.COLUMN_POSITION + " ASC"
                    + " LIMIT $2";

    private final ConnectionFactory connectionFactory;
    private final EventPayloadSerializer serializer;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public R2dbcEventStore(ConnectionFactory connectionFactory) {
        this(connectionFactory, new EventPayloadSerializer());
    }

    public R2dbcEventStore(ConnectionFactory connectionFactory, EventPayloadSerializer serializer) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    @Override
    public Mono<Void> append(String aggregateType, AggregateRootId aggregateId,
                             Flux<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        // 3.0.x 契约语义：append 以 Flux 表达事件流，实现先行物化（订阅一次即完成追加）
        return events.collectList().flatMap(eventList -> {
            if (eventList.isEmpty()) {
                return Mono.empty();
            }
            return ensureInitialized().then(Mono.usingWhen(
                    Mono.from(connectionFactory.create()),
                    connection -> Mono.from(connection.beginTransaction())
                            .then(queryCurrentVersion(connection, aggregateType, aggregateId))
                            .flatMap(actualVersion -> {
                                if (actualVersion.longValue() != expectedVersion) {
                                    return Mono.<Void>error(new AggregateVersionConflictException(
                                            aggregateType, aggregateId.asString(), expectedVersion,
                                            actualVersion.longValue()));
                                }
                                return nextPosition(connection)
                                        .flatMap(maxPosition -> executeInserts(connection, aggregateType,
                                                aggregateId, eventList, expectedVersion,
                                                maxPosition.longValue() + 1L));
                            })
                            .then(Mono.from(connection.commitTransaction()))
                            .onErrorResume(ex -> rollback(connection).then(Mono.<Void>error(ex))),
                    connection -> Mono.from(connection.close())));
        });
    }

    @Override
    public Flux<AsyncStoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return ensureInitialized().thenMany(query(READ_BY_AGGREGATE_SQL,
                statement -> statement.bind(0, aggregateType).bind(1, aggregateId.asString())));
    }

    @Override
    public Flux<AsyncStoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                       long fromVersion, long toVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return ensureInitialized().thenMany(query(READ_RANGE_SQL,
                statement -> statement.bind(0, aggregateType).bind(1, aggregateId.asString())
                        .bind(2, fromVersion).bind(3, toVersion)));
    }

    @Override
    public Flux<AsyncStoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return ensureInitialized().thenMany(query(READ_ALL_SQL,
                statement -> statement.bind(0, fromPosition).bind(1, limit)));
    }

    /** 在事务内执行查询并流式映射行：行流消费完毕后提交，异常回滚。 */
    private Flux<AsyncStoredEvent> query(String sql, Function<Statement, Statement> binder) {
        return Flux.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> Flux.from(connection.beginTransaction())
                        .thenMany(Flux.from(binder.apply(connection.createStatement(sql)).execute()))
                        .flatMap(result -> result.map((row, metadata) -> mapRow(row)))
                        .concatWith(Mono.from(connection.commitTransaction())
                                .then(Mono.<AsyncStoredEvent>empty()))
                        .onErrorResume(ex -> rollback(connection).then(Mono.<AsyncStoredEvent>error(ex))),
                connection -> Mono.from(connection.close()));
    }

    private Mono<Long> queryCurrentVersion(Connection connection, String aggregateType,
                                           AggregateRootId aggregateId) {
        return Mono.from(connection.createStatement(CURRENT_VERSION_SQL)
                        .bind(0, aggregateType)
                        .bind(1, aggregateId.asString())
                        .execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> {
                    Long value = row.get(0, Long.class);
                    return value == null ? Long.valueOf(0L) : value;
                })));
    }

    private Mono<Long> nextPosition(Connection connection) {
        return Mono.from(connection.createStatement(NEXT_POSITION_SQL).execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> {
                    Long value = row.get(0, Long.class);
                    return value == null ? Long.valueOf(0L) : value;
                })));
    }

    /** 逐事件分配版本与全局位置后按序执行 INSERT（concat 保证顺序与位置单调递增）。 */
    private Mono<Void> executeInserts(Connection connection, String aggregateType,
                                      AggregateRootId aggregateId,
                                      List<? extends DomainEvent<?>> events,
                                      long expectedVersion, long firstPosition) {
        long version = expectedVersion;
        long position = firstPosition;
        List<Mono<Long>> inserts = new ArrayList<>(events.size());
        for (DomainEvent<?> event : events) {
            version++;
            event.setAggregateVersion(new AggregateVersion(version));
            Statement statement = connection.createStatement(INSERT_SQL)
                    .bind(0, aggregateId.asString())
                    .bind(1, aggregateType)
                    .bind(2, version)
                    .bind(3, position)
                    .bind(4, event.getClass().getName())
                    .bind(5, event.getEventId().asString())
                    .bind(8, serializer.serialize(event))
                    .bind(9, LocalDateTime.ofInstant(event.getEventTimestamp().toInstant(),
                            ZoneId.systemDefault()));
            if (event.getCorrelationId() == null) {
                statement.bindNull(6, String.class);
            } else {
                statement.bind(6, event.getCorrelationId().asString());
            }
            if (event.getCausationId() == null) {
                statement.bindNull(7, String.class);
            } else {
                statement.bind(7, event.getCausationId().asString());
            }
            inserts.add(Mono.from(statement.execute())
                    .flatMap(result -> Mono.from(result.getRowsUpdated())));
            position++;
        }
        return Flux.concat(inserts).then();
    }

    /** 回滚失败不掩盖原异常。 */
    private Mono<Void> rollback(Connection connection) {
        return Mono.from(connection.rollbackTransaction())
                .onErrorResume(rollbackError -> Mono.<Void>empty());
    }

    private Mono<Void> ensureInitialized() {
        if (initialized.get()) {
            return Mono.empty();
        }
        return Mono.usingWhen(
                        Mono.from(connectionFactory.create()),
                        connection -> Flux.from(connection.createStatement(CREATE_TABLE_SQL).execute()).then(),
                        connection -> Mono.from(connection.close()))
                .doOnSuccess(v -> initialized.compareAndSet(false, true));
    }

    /** 行 → {@link AsyncStoredEvent}：元数据取列值，payload 按 {@code event_type} 反序列化。 */
    private AsyncStoredEvent mapRow(Row row) {
        String eventType = row.get(EventStoreConstants.COLUMN_EVENT_TYPE, String.class);
        DomainEvent<?> payload = serializer.deserialize(
                row.get(EventStoreConstants.COLUMN_PAYLOAD, String.class), resolveEventType(eventType));
        LocalDateTime timestamp = row.get(EventStoreConstants.COLUMN_TIMESTAMP, LocalDateTime.class);
        Long version = row.get(EventStoreConstants.COLUMN_VERSION, Long.class);
        Long position = row.get(EventStoreConstants.COLUMN_POSITION, Long.class);
        return new AsyncStoredEvent(
                EventId.valueOf(row.get(EventStoreConstants.COLUMN_EVENT_ID, String.class)),
                row.get(EventStoreConstants.COLUMN_AGGREGATE_TYPE, String.class),
                new StringAggregateRootId(row.get(EventStoreConstants.COLUMN_AGGREGATE_ID, String.class)),
                version.longValue(),
                position.longValue(),
                ZonedDateTime.of(timestamp, ZoneId.systemDefault()),
                payload,
                EventId.valueOf(row.get(EventStoreConstants.COLUMN_CORRELATION_ID, String.class)),
                EventId.valueOf(row.get(EventStoreConstants.COLUMN_CAUSATION_ID, String.class)));
    }

    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unknown event type: " + eventType, e);
        }
    }

    /** 字符串聚合根标识适配器：实体列只存字符串，读回侧重建 {@link AggregateRootId}。 */
    private static final class StringAggregateRootId implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("String");

        private final String value;

        StringAggregateRootId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }
}
