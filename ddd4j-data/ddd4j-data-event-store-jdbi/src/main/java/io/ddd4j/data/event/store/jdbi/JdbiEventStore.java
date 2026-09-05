package io.ddd4j.data.event.store.jdbi;

import com.fasterxml.jackson.databind.json.JsonMapper;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.AggregateVersion;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 JDBI 3 的 {@link EventStore} 适配器（无框架绑定，JDK8）。
 *
 * <h3>schema</h3>
 * <p>统一表 {@code DDD4J_EVENT_STORE}（与 JPA 适配器共用
 * {@link EventStoreConstants}），payload 为 TEXT（与 2.0.x/3.0.x 统一 schema
 * 对齐）。表在首次操作时通过 {@code CREATE TABLE IF NOT EXISTS} 懒创建。
 *
 * <h3>乐观锁双保险</h3>
 * <p>append 在事务内 {@code COUNT} 校验 {@code expectedVersion}，不一致抛
 * {@link AggregateVersionConflictException}；复合主键
 * {@code (aggregate_type, aggregate_id, version)} 与 {@code position} 唯一约束
 * 兜底并发漏检窗口。position 在事务内按 {@code MAX+1} 递增分配。
 *
 * <h3>版本语义</h3>
 * <p>与三分支统一契约一致：空流 {@code expectedVersion=0}，事件版本从
 * {@code expectedVersion + 1} 起分配（1-based）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public class JdbiEventStore implements EventStore {

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
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = :aggregateType"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId";

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
                    + " VALUES (:aggregateId, :aggregateType, :version, :position, :eventType,"
                    + " :eventId, :correlationId, :causationId, :payload, :timestamp)";

    private static final String READ_BY_AGGREGATE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = :aggregateType"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_RANGE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = :aggregateType"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId"
                    + " AND " + EventStoreConstants.COLUMN_VERSION + " BETWEEN :fromVersion AND :toVersion"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    private static final String READ_ALL_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_POSITION + " >= :fromPosition"
                    + " ORDER BY " + EventStoreConstants.COLUMN_POSITION + " ASC"
                    + " LIMIT :limit";

    private final Jdbi jdbi;
    private final EventPayloadSerializer serializer;
    /** uk_position 唯一约束冲突自动重试（并发 append 全局 position 兜底，回填自 3.0.x）。 */
    private final EventStoreRetry retry = new EventStoreRetry();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public JdbiEventStore(Jdbi jdbi) {
        this(jdbi, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    public JdbiEventStore(Jdbi jdbi, EventPayloadSerializer serializer) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            return;
        }
        ensureInitialized();
        try {
            retry.execute("append(" + aggregateType + ":" + aggregateId.asString() + ")", () -> {
                jdbi.useTransaction(handle -> {
                long actualVersion = handle.createQuery(CURRENT_VERSION_SQL)
                        .bind("aggregateType", aggregateType)
                        .bind("aggregateId", aggregateId.asString())
                        .mapTo(Long.class)
                        .one();
                if (actualVersion != expectedVersion) {
                    throw new AggregateVersionConflictException(
                            aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
                }
                long maxPosition = handle.createQuery(NEXT_POSITION_SQL)
                        .mapTo(Long.class)
                        .one();
                long position = maxPosition + 1L;
                long version = expectedVersion;
                for (DomainEvent<?> event : events) {
                    version++;
                    event.setAggregateVersion(new AggregateVersion(version));
                    handle.createUpdate(INSERT_SQL)
                            .bind("aggregateId", aggregateId.asString())
                            .bind("aggregateType", aggregateType)
                            .bind("version", version)
                            .bind("position", position)
                            .bind("eventType", event.getClass().getName())
                            .bind("eventId", event.getEventId().asString())
                            .bind("correlationId", event.getCorrelationId() == null ? null : event.getCorrelationId().asString())
                            .bind("causationId", event.getCausationId() == null ? null : event.getCausationId().asString())
                            .bind("payload", serializer.serialize(event))
                            .bind("timestamp", Timestamp.from(event.getEventTimestamp().toInstant()))
                            .execute();
                    position++;
                }
            });
            return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_BY_AGGREGATE_SQL)
                .bind("aggregateType", aggregateType)
                .bind("aggregateId", aggregateId.asString())
                .map(rowMapper())
                .list());
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_RANGE_SQL)
                .bind("aggregateType", aggregateType)
                .bind("aggregateId", aggregateId.asString())
                .bind("fromVersion", fromVersion)
                .bind("toVersion", toVersion)
                .map(rowMapper())
                .list());
    }

    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_ALL_SQL)
                .bind("fromPosition", fromPosition)
                .bind("limit", limit)
                .map(rowMapper())
                .list());
    }

    private RowMapper<StoredEvent> rowMapper() {
        return new StoredEventRowMapper();
    }

    private void ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            jdbi.useHandle(handle -> handle.execute(CREATE_TABLE_SQL));
        }
    }

    /** 行 → {@link StoredEvent}：元数据取列值，payload 按 {@code event_type} 反序列化。 */
    private final class StoredEventRowMapper implements RowMapper<StoredEvent> {

        @Override
        public StoredEvent map(ResultSet rs, StatementContext ctx) throws SQLException {
            String eventType = rs.getString(EventStoreConstants.COLUMN_EVENT_TYPE);
            DomainEvent<?> payload = serializer.deserialize(
                    rs.getString(EventStoreConstants.COLUMN_PAYLOAD), resolveEventType(eventType));
            Timestamp timestamp = rs.getTimestamp(EventStoreConstants.COLUMN_TIMESTAMP);
            return new StoredEvent(
                    EventId.valueOf(rs.getString(EventStoreConstants.COLUMN_EVENT_ID)),
                    rs.getString(EventStoreConstants.COLUMN_AGGREGATE_TYPE),
                    new StringAggregateRootId(rs.getString(EventStoreConstants.COLUMN_AGGREGATE_ID)),
                    rs.getLong(EventStoreConstants.COLUMN_VERSION),
                    rs.getLong(EventStoreConstants.COLUMN_POSITION),
                    ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault()),
                    payload,
                    EventId.valueOf(rs.getString(EventStoreConstants.COLUMN_CORRELATION_ID)),
                    EventId.valueOf(rs.getString(EventStoreConstants.COLUMN_CAUSATION_ID)));
        }
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
