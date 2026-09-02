package io.ddd4j.data.eventstore.jpa;

import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Spring Data JPA 的 {@link EventStore} 实现（ADR-0005，见
 * {@code docs/adr/0005-event-store-spi.md}）。
 *
 * <p>适用于任何使用 Hibernate/JPA 的运行时（Spring WebMVC/WebFlux、Helidon、Dropwizard 等）；
 * Quarkus 请用 {@code ddd4j-data-event-store-panache}，响应式请用
 * {@code ddd4j-data-event-store-r2dbc}，Javalin 请用 {@code ddd4j-data-event-store-jdbi}。
 * 本类只做「SPI 语义 ↔ JPA 持久化原语」的适配组装，并发检查、异常翻译、序列化均在本层完成
 * （{@link SpringDataStoredEventRepository} 保持零 EventStore 语义）。
 *
 * <h3>序列化器装配</h3>
 * <p>{@link EventPayloadSerializer} 是纯类（无任何容器注解，跨运行时共享），集成方需自行
 * 注册其 Bean，例如 Spring 运行时声明 {@code @Bean EventPayloadSerializer eventPayloadSerializer(ObjectMapper mapper)}。
 *
 * <p>生命周期不入 SPI（ADR-0003）：事务由 {@code @Transactional} 声明式管理，资源
 * 由运行时容器托管，无隐式 open/close。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see SpringDataStoredEventRepository
 * @see StoredEventEntity
 * @since 2.0.x
 */
@Component
public class JpaEventStore implements EventStore {

    private final SpringDataStoredEventRepository repository;

    private final EventPayloadSerializer serializer;

    /**
     * 创建 JPA 事件存储。
     *
     * @param repository Spring Data 仓储（持久化原语）
     * @param serializer 领域事件 payload 序列化器（集成方供 Bean）
     */
    public JpaEventStore(SpringDataStoredEventRepository repository, EventPayloadSerializer serializer) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁＋唯一约束双保险：本方法先以 {@link SpringDataStoredEventRepository#findCurrentVersion}
     * 的悲观写锁读取流当前版本，与 {@code expectedVersion} 不一致即抛
     * {@link AggregateVersionConflictException}（第一道，语义层）；即便并发窗口漏检，
     * {@code uk_aggregate_version} 唯一约束也会让重复版本号插入失败（第二道，数据层兜底，
     * ADR-0005）。全程运行在同一事务内，冲突或序列化失败时整体回滚，不留半截流。
     */
    @Override
    @Transactional
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(events, "events must not be null");
        long actualVersion = repository.findCurrentVersion(aggregateType, aggregateId.asString());
        if (actualVersion != expectedVersion) {
            throw new AggregateVersionConflictException(
                    aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
        }
        ZonedDateTime now = ZonedDateTime.now();
        long version = expectedVersion;
        for (DomainEvent<?> event : events) {
            version++;
            StoredEventEntity entity = new StoredEventEntity();
            entity.setEventId(event.getEventId().asString());
            entity.setAggregateType(aggregateType);
            entity.setAggregateId(aggregateId.asString());
            entity.setVersion(version);
            entity.setEventType(event.getClass().getName());
            entity.setPayload(serializer.serialize(event));
            entity.setCorrelationId(event.getCorrelationId() != null ? event.getCorrelationId().asString() : null);
            entity.setCausationId(event.getCausationId() != null ? event.getCausationId().asString() : null);
            entity.setCreatedAt(now);
            repository.save(entity);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        return repository.findByAggregateTypeAndAggregateIdOrderByVersionAsc(
                        aggregateType, aggregateId.asString())
                .stream().map(this::toStoredEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        return repository.findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
                        aggregateType, aggregateId.asString(), fromVersion, toVersion)
                .stream().map(this::toStoredEvent).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return repository.findByPositionGreaterThanEqualOrderByPositionAsc(fromPosition)
                .stream().limit(limit).map(this::toStoredEvent).toList();
    }

    /**
     * 把持久化实体重建为 {@link StoredEvent}：事件类型经 {@link Class#forName} 还原，
     * payload 经 {@link EventPayloadSerializer#deserialize} 反序列化，
     * {@code eventId}／{@code correlationId}／{@code causationId} 经
     * {@link EventId#valueOf}（空安全）解析。
     *
     * <p>{@code position} 由数据库生成（实体上无 setter）：持久化读回必非空，
     * 此处 fail-loud 断言——瞬态实体（未落库、无 position）进入重建路径视为
     * 编程错误，直接抛 {@link NullPointerException} 而非静默按 0 处理。
     *
     * @param entity 持久化实体
     * @return 重建的持久化事件快照
     * @throws NullPointerException entity 未持久化（position 为 null）
     * @throws IllegalStateException eventType 类不存在（事件类被重命名/删除后旧流不可读）
     */
    private StoredEvent toStoredEvent(StoredEventEntity entity) {
        DomainEvent<?> payload = serializer.deserialize(entity.getPayload(), resolveEventType(entity.getEventType()));
        return new StoredEvent(
                EventId.valueOf(entity.getEventId()),
                entity.getAggregateType(),
                new StringAggregateRootId(entity.getAggregateId()),
                entity.getVersion(),
                Objects.requireNonNull(entity.getPosition(),
                        "position must not be null in read path (transient entities unsupported)"),
                entity.getCreatedAt(),
                payload,
                EventId.valueOf(entity.getCorrelationId()),
                EventId.valueOf(entity.getCausationId()));
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
     * 字符串聚合根标识适配器：实体列 {@code aggregate_id} 只存字符串，读回侧需重建
     * {@link AggregateRootId} 接口实例（{@code StringEntityId} 仅实现
     * {@code EntityId}，不满足 {@link StoredEvent} 构造器约束）。
     *
     * <p>三方法契约与 {@code StringEntityId} 一致：类型固定 {@code String}、
     * 原值与 {@code 类型:值} 形式。
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
