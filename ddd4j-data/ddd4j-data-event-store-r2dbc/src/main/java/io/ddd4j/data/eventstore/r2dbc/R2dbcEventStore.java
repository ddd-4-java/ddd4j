package io.ddd4j.data.eventstore.r2dbc;

import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.eventstore.AsyncEventStore;
import io.ddd4j.data.eventstore.AggregateVersionConflictException;
import io.ddd4j.data.eventstore.StoredEvent;
import io.ddd4j.data.eventstore.jackson.EventPayloadSerializer;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/**
 * 基于 R2DBC 的 {@link AsyncEventStore} 实现（ADR-0005，见
 * {@code docs/adr/0005-event-store-spi.md}）。
 *
 * <p>纯 {@code io.r2dbc.spi} 落地：全部读写经 {@link Connection} 原语
 * （createStatement／execute／map）组装为 Mono／Flux，<b>零 Spring</b>——
 * WebFlux 与 Vert.x 等响应式运行时皆可直接装配（ADR-0003 多运行时对齐），
 * 也因此可对本模块立 ArchUnit {@code no_spring} 规则。响应式事务由
 * {@link Connection#beginTransaction}／{@link Connection#commitTransaction}／
 * {@link Connection#rollbackTransaction} 原生提供，无需任何事务管理器。
 *
 * <h3>集成方装配</h3>
 * <p>本类为纯类（零容器注解、非容器托管）：集成方在应用装配代码中手动
 * {@code new R2dbcEventStore(connectionFactory, serializer)} 并自行管理生命周期——
 * {@link ConnectionFactory}（如 {@code ConnectionFactories.get(...)} 或 r2dbc-pool
 * 包装）与 {@link EventPayloadSerializer}（mapper 建议
 * {@code findAndAddModules} 构建）均由集成方提供。生命周期不入 SPI（ADR-0003）：
 * {@link Connection} 由本类按操作粒度 create／close，无隐式全局状态。
 *
 * <h3>表契约</h3>
 * <p>目标表 {@code ddd4j_stored_event} 与 -jpa/-panache/-jdbi 模块同构（含
 * {@code tenant_id} 列与 {@code uk_aggregate_version} 唯一约束）：本类插入 9 个
 * 业务列（{@code tenant_id} 暂不写入，保持可空），{@code position} 由数据库自增
 * 生成，DDL 由集成方负责建表。
 *
 * <h3>事务边界与兜底</h3>
 * <p>append 的版本校验与逐条插入在同一 {@code beginTransaction..commit} 区间内
 * 原子完成（连接以 {@code Mono/Flux.usingWhen} 生命周期托管：成功即 commit 后
 * close，异常／取消即 rollback 后 close）；即便并发窗口漏检，
 * {@code uk_aggregate_version} 唯一约束也会让重复版本号插入失败（第二道，
 * 数据层兜底，ADR-0005）。读路径无事务，连接在读流终结后 close。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class R2dbcEventStore implements AsyncEventStore {

    private static final String INSERT_SQL = "insert into ddd4j_stored_event ("
            + "event_id, aggregate_type, aggregate_id, version, event_type, "
            + "payload, correlation_id, causation_id, created_at) values ("
            + "$1, $2, $3, $4, $5, $6, $7, $8, $9)";

    private final ConnectionFactory connectionFactory;

    private final EventPayloadSerializer serializer;

    /**
     * 创建 R2DBC 事件存储。
     *
     * @param connectionFactory R2DBC 连接工厂（集成方装配，可为 r2dbc-pool 池化）
     * @param serializer        领域事件 payload 序列化器（集成方构建）
     */
    public R2dbcEventStore(ConnectionFactory connectionFactory, EventPayloadSerializer serializer) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁＋唯一约束双保险：本方法在 {@code beginTransaction..commit} 单事务内
     * 先以 {@code select coalesce(max(version), 0)} 读取流当前版本（空流为 0），与
     * {@code expectedVersion} 不一致即以 {@link AggregateVersionConflictException}
     * 错误信号终止并 rollback（第一道，语义层）；即便并发窗口漏检，
     * {@code uk_aggregate_version} 唯一约束也会让重复版本号插入失败（第二道，
     * 数据层兜底，ADR-0005）。冲突或序列化失败时事务整体回滚，不留半截流。
     *
     * <p>事件流先经 {@code collectList()} 物化再进入连接链：事务区间内逐条
     * {@code concatMap} 插入（严格保序，r2dbc 无 JDBC batch 语义），全部成功才
     * {@code commitTransaction}。
     */
    @Override
    public Mono<Void> append(String aggregateType, AggregateRootId aggregateId,
                             Flux<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(events, "events must not be null");
        return events.collectList().flatMap(eventList -> Mono.usingWhen(
                connectionFactory.create(),
                connection -> Mono.from(connection.beginTransaction())
                        .then(currentVersion(connection, aggregateType, aggregateId))
                        .flatMap(actualVersion -> {
                            if (actualVersion != expectedVersion) {
                                return Mono.error(new AggregateVersionConflictException(
                                        aggregateType, aggregateId.asString(), expectedVersion, actualVersion));
                            }
                            AtomicLong version = new AtomicLong(expectedVersion);
                            return Flux.fromIterable(eventList)
                                    .concatMap(event -> insertRow(connection, aggregateType, aggregateId,
                                            version.incrementAndGet(), event))
                                    .then(Mono.from(connection.commitTransaction()));
                        }),
                connection -> Mono.from(connection.close()),
                (connection, ex) -> rollbackAndClose(connection),
                R2dbcEventStore::rollbackAndClose));
    }

    @Override
    public Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        return query("select * from ddd4j_stored_event "
                        + "where aggregate_type = $1 and aggregate_id = $2 order by version",
                statement -> statement.bind(0, aggregateType).bind(1, aggregateId.asString()));
    }

    @Override
    public Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        return query("select * from ddd4j_stored_event "
                        + "where aggregate_type = $1 and aggregate_id = $2 "
                        + "and version between $3 and $4 order by version",
                statement -> statement.bind(0, aggregateType).bind(1, aggregateId.asString())
                        .bind(2, fromVersion).bind(3, toVersion));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code limit} 直接下推为 SQL {@code limit $2}（真数据库分页，r2dbc-h2
     * 实证支持 limit 参数）——对齐 4.3 遗留改进项：-jpa 实现是先全量拉取再内存
     * {@code stream().limit()}，本实现从 SQL 层截断，投影断线续传大位点重读时
     * 不会物化多余行。
     */
    @Override
    public Flux<StoredEvent> readAll(long fromPosition, int limit) {
        return query("select * from ddd4j_stored_event "
                        + "where position >= $1 order by position limit $2",
                statement -> statement.bind(0, fromPosition).bind(1, limit));
    }

    /**
     * 读路径通用组装：连接以 {@code Flux.usingWhen} 托管（读流终结／出错／取消即
     * close），SQL 经 binder 绑参后执行并逐行重建 {@link StoredEvent}。
     *
     * @param sql    查询 SQL（占位符 {@code $n}）
     * @param binder 占位符绑参
     * @return 行重建的持久化事件流
     */
    private Flux<StoredEvent> query(String sql, UnaryOperator<Statement> binder) {
        return Flux.usingWhen(
                connectionFactory.create(),
                connection -> Flux.from(binder.apply(connection.createStatement(sql)).execute())
                        .flatMap(result -> result.map((row, metadata) -> toStoredEvent(row))),
                connection -> Mono.from(connection.close()),
                (connection, ex) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close()));
    }

    /**
     * 读取流当前版本（空流为 0）。
     *
     * @param connection    事务内连接
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @return 流的当前版本号
     */
    private Mono<Long> currentVersion(Connection connection, String aggregateType, AggregateRootId aggregateId) {
        return Mono.from(connection.createStatement(
                        "select coalesce(max(version), 0) from ddd4j_stored_event "
                                + "where aggregate_type = $1 and aggregate_id = $2")
                        .bind(0, aggregateType)
                        .bind(1, aggregateId.asString())
                        .execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get(0, Long.class))));
    }

    /**
     * 插入单条事件行（9 业务列）。可空追踪维度（correlationId／causationId）无值时
     * 经 {@code bindNull} 显式绑定 null——r2dbc 不接受直接 {@code bind(idx, null)}。
     *
     * @param connection    事务内连接
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param version       本条事件的流内版本号
     * @param event         领域事件
     * @return 完成信号
     */
    private Mono<Void> insertRow(Connection connection, String aggregateType, AggregateRootId aggregateId,
                                 long version, DomainEvent<?> event) {
        Statement statement = connection.createStatement(INSERT_SQL)
                .bind(0, event.getEventId().asString())
                .bind(1, aggregateType)
                .bind(2, aggregateId.asString())
                .bind(3, version)
                .bind(4, event.getClass().getName())
                .bind(5, serializer.serialize(event));
        if (event.getCorrelationId() != null) {
            statement.bind(6, event.getCorrelationId().asString());
        } else {
            statement.bindNull(6, String.class);
        }
        if (event.getCausationId() != null) {
            statement.bind(7, event.getCausationId().asString());
        } else {
            statement.bindNull(7, String.class);
        }
        return Mono.from(statement
                        .bind(8, ZonedDateTime.now().toOffsetDateTime())
                        .execute())
                .flatMap(result -> Mono.from(result.getRowsUpdated()))
                .then();
    }

    /**
     * 异常／取消路径的连接收尾：rollback（容忍「事务未开启」的回滚失败，不吞原始
     * 异常）后 close。
     *
     * @param connection 事务内连接
     * @return 完成信号
     */
    private static Mono<Void> rollbackAndClose(Connection connection) {
        return Mono.from(connection.rollbackTransaction())
                .onErrorResume(e -> Mono.empty())
                .then(Mono.from(connection.close()));
    }

    /**
     * 把结果集行重建为 {@link StoredEvent}：事件类型经 {@link Class#forName} 还原，
     * payload 经 {@link EventPayloadSerializer#deserialize} 反序列化，
     * {@code eventId}／{@code correlationId}／{@code causationId} 经
     * {@link EventId#valueOf}（空安全）解析。
     *
     * <p>{@code created_at}（{@code timestamp with time zone} 列）经实证由 r2dbc-h2
     * 回读为 {@link OffsetDateTime}，此处 {@code toZonedDateTime()} 适配。
     *
     * @param row 当前行（列名与表契约一致）
     * @return 重建的持久化事件快照
     * @throws IllegalStateException eventType 类不存在（事件类被重命名/删除后旧流不可读）
     */
    private StoredEvent toStoredEvent(Row row) {
        DomainEvent<?> payload = serializer.deserialize(
                row.get("payload", String.class), resolveEventType(row.get("event_type", String.class)));
        return new StoredEvent(
                EventId.valueOf(row.get("event_id", String.class)),
                row.get("aggregate_type", String.class),
                new StringAggregateRootId(row.get("aggregate_id", String.class)),
                row.get("version", Long.class),
                row.get("position", Long.class),
                row.get("created_at", OffsetDateTime.class).toZonedDateTime(),
                payload,
                EventId.valueOf(row.get("correlation_id", String.class)),
                EventId.valueOf(row.get("causation_id", String.class)));
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
     * 原值与 {@code 类型:值} 形式（与 -jpa/-panache/-jdbi 模块内同名适配器对齐）。
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
