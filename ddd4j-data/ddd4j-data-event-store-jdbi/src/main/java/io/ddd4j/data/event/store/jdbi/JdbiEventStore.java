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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.*;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.Argument;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    /** \u9996\u6b21\u4f7f\u7528\u65f6\u521b\u5efa\u4e8b\u4ef6\u8868\u7684\u6570\u636e\u5e93\u8bed\u53e5\u3002 */
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
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + ", "
                    + EventStoreConstants.COLUMN_VERSION + "), "
                    + "CONSTRAINT uk_position UNIQUE (" + EventStoreConstants.COLUMN_POSITION + ")"
                    + ")";

    /** \u67e5\u8be2\u5f53\u524d\u805a\u5408\u5df2\u6301\u4e45\u5316\u7248\u672c\u7684\u8bed\u53e5\u3002 */
    private static final String CURRENT_VERSION_SQL =
            "SELECT COUNT(*) FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = :aggregateType"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId";

    /** \u4f1a\u8bdd\u4e2d\u8bfb\u53d6\u4e0b\u4e00\u4e2a\u5168\u5c40\u4e8b\u4ef6\u4f4d\u7f6e\u7684\u8bed\u53e5\u3002 */
    private static final String NEXT_POSITION_SQL =
            "SELECT COALESCE(MAX(" + EventStoreConstants.COLUMN_POSITION + "), 0) FROM "
                    + EventStoreConstants.TABLE_NAME;

    /** \u8ffd\u52a0\u4e00\u4e2a\u9886\u57df\u4e8b\u4ef6\u6301\u4e45\u5316\u884c\u7684\u8bed\u53e5\u3002 */
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
                    + ", " + EventStoreConstants.COLUMN_TIMESTAMP
                    + ") VALUES (:aggregateId, :aggregateType, :version, :position, :eventType, :eventId, :correlationId, :causationId, :payload, :timestamp)";

    /** \u6309\u805a\u5408\u6807\u8bc6\u53ca\u7248\u672c\u8303\u56f4\u8bfb\u53d6\u4e8b\u4ef6\u6d41\u7684\u8bed\u53e5\u3002 */
    private static final String READ_BY_AGGREGATE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = :aggregateType"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    /** \u6309\u805a\u5408\u6807\u8bc6\u53ca\u6307\u5b9a\u7248\u672c\u8fb9\u754c\u8bfb\u53d6\u4e8b\u4ef6\u7684 SQL\u3002 */
    private static final String READ_BY_VERSION_RANGE_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " = :aggregateType"
                    + " AND " + EventStoreConstants.COLUMN_AGGREGATE_ID + " = :aggregateId"
                    + " AND " + EventStoreConstants.COLUMN_VERSION + " BETWEEN :fromVersion AND :toVersion"
                    + " ORDER BY " + EventStoreConstants.COLUMN_VERSION + " ASC";

    /** \u6309\u5168\u5c40\u4f4d\u7f6e\u6279\u91cf\u8bfb\u53d6\u4e8b\u4ef6\u7684\u8bed\u53e5\u3002 */
    private static final String READ_ALL_SQL =
            "SELECT * FROM " + EventStoreConstants.TABLE_NAME
                    + " WHERE " + EventStoreConstants.COLUMN_POSITION + " >= :fromPosition"
                    + " ORDER BY " + EventStoreConstants.COLUMN_POSITION + " ASC"
                    + " LIMIT :limit";

    /** \u8d1f\u8d23\u83b7\u53d6 JDBC \u8fde\u63a5\u5e76\u6267\u884c SQL \u64cd\u4f5c\u7684\u8bbf\u95ee\u5165\u53e3\u3002 */
    private final Jdbi jdbi;
    /** \u5728\u6570\u636e\u5e93\u53ef\u91cd\u8bd5\u6545\u969c\u540e\u91cd\u65b0\u6267\u884c\u64cd\u4f5c\u7684\u7b56\u7565\u3002 */
    private final EventStoreRetry retry;
    /** \u5c06\u9886\u57df\u4e8b\u4ef6\u8d1f\u8f7d\u5199\u4e3a\u5b58\u50a8\u683c\u5f0f\u5e76\u4ece\u5b58\u50a8\u683c\u5f0f\u6062\u590d\u7684\u5e8f\u5217\u5316\u5668\u3002 */
    private final EventPayloadSerializer serializer;
    /** \u5b58\u50a8\u521d\u59cb\u5316\u53ca\u5176\u5171\u4eab\u5b8c\u6210\u72b6\u6001\u3002 */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 创建 JDBI 事件存储。
     *
     * @param jdbi JDBI 实例（集成方装配，可包连接池 DataSource）
     * @throws NullPointerException jdbi 为 null 时抛出
     */
    public JdbiEventStore(Jdbi jdbi) {
        this(jdbi, new EventStoreRetry(), new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    /**
     * 创建 JDBI 事件存储（指定重试策略；主要供测试使用）。
     *
     * @param jdbi  JDBI 实例
     * @param retry 重试策略
     */
    public JdbiEventStore(Jdbi jdbi, EventStoreRetry retry) {
        this(jdbi, retry, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    /**
     * \u521b\u5efa\u4f7f\u7528\u6307\u5b9a\u91cd\u8bd5\u7b56\u7565\u548c\u8f7d\u8377\u5e8f\u5217\u5316\u5668\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002
     * @param jdbi JDBI \u5b9e\u4f8b
     * @param retry \u91cd\u8bd5\u7b56\u7565
     * @param serializer \u4e8b\u4ef6\u8f7d\u8377\u5e8f\u5217\u5316\u5668
     */
    public JdbiEventStore(Jdbi jdbi, EventStoreRetry retry, EventPayloadSerializer serializer) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
        this.retry = Objects.requireNonNull(retry, "retry must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * {@inheritDoc}
     *
     *  <p>\u4e50\u89c2\u9501\uff1a\u4e8b\u52a1\u5185\u5148\u67e5\u8be2\u5f53\u524d\u6d41\u4e8b\u4ef6\u6570\uff08\u5373\u5f53\u524d\u7248\u672c\uff09\uff0c\u4e0e {@code expectedVersion}
     *  \u4e0d\u4e00\u81f4\u5373\u629b {@link IllegalStateException} \u5e76\u56de\u6eda\u3002\u591a\u4e8b\u4ef6\u5728\u540c\u4e00\u4e8b\u52a1\u5185\u539f\u5b50\u63d0\u4ea4\uff0c
     *  \u4e0d\u7559\u534a\u622a\u6d41\u3002
     *
     *  <p>position \u751f\u6210\uff1a\u4e8b\u52a1\u5185\u9010\u6761\u4ee5 {@code COALESCE(MAX(position), 0) + 1} \u9012\u589e\uff0c
     *  \u4e0e JPA/R2DBC \u4fa7\u7b56\u7565\u4e00\u81f4\u3002\u9996\u6761\u7684 position \u5728\u5faa\u73af\u524d\u9884\u53d6 maxPos\uff0c
     *  \u540e\u7eed\u6bcf\u6761 +1\uff0c\u907f\u514d\u6bcf\u6761\u90fd\u6267\u884c MAX \u67e5\u8be2\u3002
     */
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
                    long maxPos = handle.createQuery(NEXT_POSITION_SQL)
                            .mapTo(Long.class)
                            .one();
                    long position = maxPos + 1L;
                    long version = expectedVersion;
                    for (DomainEvent<?> event : events) {
                        handle.createUpdate(INSERT_SQL)
                                .bind("aggregateId", aggregateId.asString())
                                .bind("aggregateType", aggregateType)
                                .bind("version", ++version)
                                .bind("position", position)
                                .bind("eventType", event.getClass().getName())
                                .bind("eventId", event.getEventId().asString())
                                .bind("correlationId", event.getCorrelationId() == null ? null : event.getCorrelationId().asString())
                                .bind("causationId", event.getCausationId() == null ? null : event.getCausationId().asString())
                                .bind("payload", serializer.serialize(event))
                                .bind("timestamp", (Argument) (index, statement, context) -> statement.setObject(
                                        index, event.getEventTimestamp()
                                                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()))
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

    /**
     * {@inheritDoc}
     *
     *  <p>\u6309\u7248\u672c\u5347\u5e8f\u8bfb\u53d6\u6307\u5b9a\u805a\u5408\u7684\u5168\u90e8\u4e8b\u4ef6\u3002
     *  \u4e8b\u4ef6\u8f7d\u8377\u901a\u8fc7 {@link EventPayloadSerializer} \u53cd\u5e8f\u5217\u5316\uff0c\u7c7b\u578b\u65e0\u6cd5\u8fd8\u539f\u65f6\u629b\u51fa {@link IllegalStateException}\u3002
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_BY_AGGREGATE_SQL)
                .bind("aggregateType", aggregateType)
                .bind("aggregateId", aggregateId.asString())
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    /** {@inheritDoc} */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_BY_VERSION_RANGE_SQL)
                .bind("aggregateType", aggregateType)
                .bind("aggregateId", aggregateId.asString())
                .bind("fromVersion", fromVersion)
                .bind("toVersion", toVersion)
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    /**
     * {@inheritDoc}
     *
     *  <p>\u6309\u5168\u5c40 position \u5347\u5e8f\u5206\u9875\u8bfb\u53d6\u4e8b\u4ef6\u3002
     *  {@code limit} \u4e0b\u63a8\u4e3a SQL {@code LIMIT}\uff0c\u4ece SQL \u5c42\u622a\u65ad\u907f\u514d\u7269\u5316\u591a\u4f59\u884c\u3002
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        ensureInitialized();
        return jdbi.withHandle(handle -> handle.createQuery(READ_ALL_SQL)
                .bind("fromPosition", fromPosition)
                .bind("limit", limit)
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    /** \u786e\u4fdd\u8868\u5df2\u521b\u5efa\uff08\u61d2\u521d\u59cb\u5316\uff0c\u4ec5\u6267\u884c\u4e00\u6b21\uff09\u3002 */
    private void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        synchronized (initialized) {
            if (!initialized.get()) {
                jdbi.useHandle(handle -> handle.execute(CREATE_TABLE_SQL));
                initialized.set(true);
            }
        }
    }

    /**
     * 将结果集行重建为 {@link StoredEvent}。
     *
     * <p>事件类型经 {@code Class.forName} 还原，payload 经 {@link EventPayloadSerializer#deserialize}
     * 反序列化。若类不存在（被删除/重命名），抛出 {@link IllegalStateException}。
     *
     * <p>时间戳处理：H2 JDBC 驱动对 TIMESTAMP 列返回 {@link LocalDateTime}，
     * 此处统一先取 {@link LocalDateTime} 再转 {@link java.time.Instant}（UTC 偏移），
     * 与 R2DBC 实现一致。
     *
     * @param rs 当前行（列名与表契约一致）
     * @return 重建的存储事件
     * @throws SQLException JDBC 列读取失败
     */
    private StoredEvent toStoredEvent(ResultSet rs) throws SQLException {
        String payload = rs.getString(EventStoreConstants.COLUMN_PAYLOAD);
        String eventType = rs.getString(EventStoreConstants.COLUMN_EVENT_TYPE);
        DomainEvent<?> event = serializer.deserialize(payload, resolveEventType(eventType));
        // H2 JDBC 对 TIMESTAMP 列返回 LocalDateTime，转为 Instant（与 R2DBC 实现一致）
        LocalDateTime timestamp = rs.getObject(EventStoreConstants.COLUMN_TIMESTAMP, LocalDateTime.class);
        if (Objects.isNull(timestamp)) {
            throw new IllegalStateException("Missing persisted event timestamp");
        }
        return new StoredEvent(
                EventId.valueOf(rs.getString(EventStoreConstants.COLUMN_EVENT_ID)),
                rs.getString(EventStoreConstants.COLUMN_AGGREGATE_TYPE),
                new StringAggregateRootId(rs.getString(EventStoreConstants.COLUMN_AGGREGATE_ID)),
                rs.getLong(EventStoreConstants.COLUMN_VERSION),
                rs.getLong(EventStoreConstants.COLUMN_POSITION),
                timestamp.atZone(ZoneOffset.UTC),
                event,
                EventId.valueOf(rs.getString(EventStoreConstants.COLUMN_CORRELATION_ID)),
                EventId.valueOf(rs.getString(EventStoreConstants.COLUMN_CAUSATION_ID)));
    }

    /**
     * \u6309\u6301\u4e45\u5316\u7684\u5168\u9650\u5b9a\u7c7b\u540d\u89e3\u6790\u4e8b\u4ef6\u8d1f\u8f7d\u7c7b\u578b\u3002
     * @param eventType \u4e8b\u4ef6\u7684 Java \u7c7b\u578b\u6216\u6301\u4e45\u5316\u7c7b\u578b\u540d\u79f0
     * @return \u7528\u4e8e\u53cd\u5e8f\u5217\u5316\u7684\u9886\u57df\u4e8b\u4ef6\u7c7b
     */
    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unknown event type: " + eventType, exception);
        }
    }

    /** \u5c06\u4e8b\u4ef6\u5b58\u50a8\u4e2d\u7684\u5b57\u7b26\u4e32\u6807\u8bc6\u9002\u914d\u4e3a\u805a\u5408\u6839\u6807\u8bc6\u3002 */
    private final static class StringAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 6652084670470061437L;

        /** \u6765\u81ea\u4e8b\u4ef6\u8bb0\u5f55\u7684\u539f\u59cb\u5b57\u7b26\u4e32\u6807\u8bc6\u3002 */
        private final String value;

        /** \u53cd\u5e8f\u5217\u5316\u5b57\u7b26\u4e32\u805a\u5408\u6807\u8bc6\u65f6\u4f7f\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u3002 */
        private static final StringEntityType TYPE = new StringEntityType("String");

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        @JsonValue
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u5c06\u6301\u4e45\u5316\u8bb0\u5f55\u91cc\u7684\u5b57\u7b26\u4e32\u5305\u88c5\u6210\u805a\u5408\u6839\u6807\u8bc6\u3002
         * @param value \u805a\u5408\u6839\u6807\u8bc6\u7684\u539f\u59cb\u5b57\u7b26\u4e32
         */
        @JsonCreator()
        private StringAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            StringAggregateRootId other = (StringAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "StringAggregateRootId[value=" + value + "]";
        }
    }
}
