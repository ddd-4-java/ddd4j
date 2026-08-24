package io.ddd4j.data.eventstore.jdbi;

import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.eventstore.AggregateVersionConflictException;
import io.ddd4j.data.eventstore.EventStore;
import io.ddd4j.data.eventstore.StoredEvent;
import io.ddd4j.data.eventstore.jackson.EventPayloadSerializer;
import org.jdbi.v3.core.Jdbi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于 JDBI 的 {@link EventStore} 实现（ADR-0005，见
 * {@code docs/adr/0005-event-store-spi.md}）。
 *
 * <p>SQL-first：全部读写以手写 SQL 经 jdbi3-core 的 Statement/Handle 原语执行
 * （无注解 SQL Object），适用于 Javalin／Vert.x 等轻量运行时；Spring 系运行时请用
 * {@code ddd4j-data-event-store-jpa}，Quarkus 请用 {@code ddd4j-data-event-store-panache}，
 * 响应式请用 {@code ddd4j-data-event-store-r2dbc}。
 *
 * <h3>集成方装配</h3>
 * <p>本类为纯类（零容器注解、非容器托管）：Javalin／Vert.x 集成方在应用装配代码中
 * 手动 {@code new JdbiEventStore(jdbi, serializer)} 并自行管理其生命周期——{@code Jdbi}
 * 实例（可包连接池 DataSource）与 {@link EventPayloadSerializer}（mapper 建议
 * {@code findAndAddModules} 构建）均由集成方提供。
 *
 * <h3>表契约</h3>
 * <p>目标表 {@code ddd4j_stored_event} 与 -jpa/-panache 模块同构（含 {@code tenant_id}
 * 列与 {@code uk_aggregate_version} 唯一约束）：本类插入 9 个业务列（{@code tenant_id}
 * 暂不写入，保持可空），{@code position} 由数据库自增生成，DDL 由集成方负责建表。
 *
 * <p>生命周期不入 SPI（ADR-0003）：append 的原子性由 {@link Jdbi#useTransaction} 保证，
 * 连接资源由 {@code Jdbi} 底层 DataSource 管理，无隐式 open/close。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class JdbiEventStore implements EventStore {

    private final Jdbi jdbi;

    private final EventPayloadSerializer serializer;

    /**
     * 创建 JDBI 事件存储。
     *
     * @param jdbi       JDBI 实例（集成方装配，可包连接池 DataSource）
     * @param serializer 领域事件 payload 序列化器（集成方构建）
     */
    public JdbiEventStore(Jdbi jdbi, EventPayloadSerializer serializer) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁＋唯一约束双保险：本方法在 {@link Jdbi#useTransaction} 单事务内先以
     * {@code select coalesce(max(version), 0)} 读取流当前版本（空流为 0），与
     * {@code expectedVersion} 不一致即抛 {@link AggregateVersionConflictException}
     * （第一道，语义层）；即便并发窗口漏检，{@code uk_aggregate_version} 唯一约束
     * 也会让重复版本号插入失败（第二道，数据层兜底，ADR-0005）。冲突或序列化失败时
     * 事务整体回滚，不留半截流。
     */
    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(events, "events must not be null");
        jdbi.useTransaction(handle -> {
            long actualVersion = handle.createQuery(
                            "select coalesce(max(version), 0) from ddd4j_stored_event "
                                    + "where aggregate_type = :type and aggregate_id = :id")
                    .bind("type", aggregateType)
                    .bind("id", aggregateId.asString())
                    .mapTo(Long.class)
                    .one();
            if (actualVersion != expectedVersion) {
                throw new AggregateVersionConflictException(
                        aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
            }
            ZonedDateTime now = ZonedDateTime.now();
            long version = expectedVersion;
            for (DomainEvent<?> event : events) {
                version++;
                handle.createUpdate(
                                "insert into ddd4j_stored_event ("
                                        + "event_id, aggregate_type, aggregate_id, version, event_type, "
                                        + "payload, correlation_id, causation_id, created_at) values ("
                                        + ":eventId, :type, :id, :version, :eventType, "
                                        + ":payload, :correlationId, :causationId, :createdAt)")
                        .bind("eventId", event.getEventId().asString())
                        .bind("type", aggregateType)
                        .bind("id", aggregateId.asString())
                        .bind("version", version)
                        .bind("eventType", event.getClass().getName())
                        .bind("payload", serializer.serialize(event))
                        // 可空追踪维度：无因果事件时绑定 null（列可空）
                        .bind("correlationId", event.getCorrelationId() != null
                                ? event.getCorrelationId().asString() : null)
                        .bind("causationId", event.getCausationId() != null
                                ? event.getCausationId().asString() : null)
                        .bind("createdAt", now)
                        .execute();
            }
        });
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "select * from ddd4j_stored_event "
                                + "where aggregate_type = :type and aggregate_id = :id order by version")
                .bind("type", aggregateType)
                .bind("id", aggregateId.asString())
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "select * from ddd4j_stored_event "
                                + "where aggregate_type = :type and aggregate_id = :id "
                                + "and version between :from and :to order by version")
                .bind("type", aggregateType)
                .bind("id", aggregateId.asString())
                .bind("from", fromVersion)
                .bind("to", toVersion)
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code limit} 直接下推为 SQL {@code limit :limit}（真数据库分页）——对齐
     * 4.3 遗留改进项：-jpa 实现是先全量拉取再内存 {@code stream().limit()}，
     * 本实现从 SQL 层截断，投影断线续传大位点重读时不会物化多余行。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        "select * from ddd4j_stored_event where position >= :pos "
                                + "order by position limit :limit")
                .bind("pos", fromPosition)
                .bind("limit", limit)
                .map((rs, ctx) -> toStoredEvent(rs))
                .list());
    }

    /**
     * 把结果集行重建为 {@link StoredEvent}：事件类型经 {@link Class#forName} 还原，
     * payload 经 {@link EventPayloadSerializer#deserialize} 反序列化，
     * {@code eventId}／{@code correlationId}／{@code causationId} 经
     * {@link EventId#valueOf}（空安全）解析。
     *
     * @param rs 当前行（列名与表契约一致）
     * @return 重建的持久化事件快照
     * @throws SQLException JDBC 列读取失败
     * @throws IllegalStateException eventType 类不存在（事件类被重命名/删除后旧流不可读）
     */
    private StoredEvent toStoredEvent(ResultSet rs) throws SQLException {
        DomainEvent<?> payload = serializer.deserialize(rs.getString("payload"), resolveEventType(rs.getString("event_type")));
        return new StoredEvent(
                EventId.valueOf(rs.getString("event_id")),
                rs.getString("aggregate_type"),
                new StringAggregateRootId(rs.getString("aggregate_id")),
                rs.getLong("version"),
                rs.getLong("position"),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneId.systemDefault()),
                payload,
                EventId.valueOf(rs.getString("correlation_id")),
                EventId.valueOf(rs.getString("causation_id")));
    }

    /**
     * 按限定名还原事件类型。
     *
     * @param eventType 事件类型限定名
     * @return 事件类型
     * @throws IllegalStateException 类不存在
     */
    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unknown event type: " + eventType, e);
        }
    }

    /**
     * 字符串聚合根标识适配器：表列 {@code aggregate_id} 只存字符串，读回侧需重建
     * {@link AggregateRootId} 接口实例（{@code StringEntityId} 仅实现
     * {@code EntityId}，不满足 {@link StoredEvent} 构造器约束）。
     *
     * <p>三方法契约与 {@code StringEntityId} 一致：类型固定 {@code String}、
     * 原值与 {@code 类型:值} 形式（与 -jpa/-panache 模块内同名适配器对齐）。
     */
    private record StringAggregateRootId(String value) implements AggregateRootId {

        /** 字符串聚合根标识的固定类型。 */
        private static final StringEntityType TYPE = new StringEntityType("String");

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        @JsonValue
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }
}
