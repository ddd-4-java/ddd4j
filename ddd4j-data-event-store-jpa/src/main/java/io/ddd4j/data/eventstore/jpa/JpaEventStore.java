package io.ddd4j.data.eventstore.jpa;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.AggregateVersion;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于 JPA 2.2（javax.persistence）的 {@link EventStore} 适配器。
 *
 * <h3>事务管理</h3>
 * <p>程序化事务（{@link EntityTransaction}）：{@code append} 在方法内开启事务，
 * 冲突或异常时整体回滚；{@code read} / {@code readAll} 同样包裹只读事务保证读隔离。
 * 调用方无需（也不应）在外层包裹事务。
 *
 * <h3>乐观锁双保险</h3>
 * <p>第一道：append 前 {@code COUNT} 校验 {@code expectedVersion}，不一致抛
 * {@link AggregateVersionConflictException}；第二道：复合主键
 * {@code (aggregate_type, aggregate_id, version)} 与 {@code position} 唯一约束
 * 在并发漏检窗口兜底（重复版本/位置插入失败，事务回滚）。
 *
 * <h3>版本语义</h3>
 * <p>与三分支统一契约一致：空流 {@code expectedVersion=0}，事件版本从
 * {@code expectedVersion + 1} 起分配（1-based），流内版本 = 已存事件数。
 *
 * <h3>schema</h3>
 * <p>统一表 {@code DDD4J_EVENT_STORE}，payload 为 TEXT（与 2.0.x/3.0.x 对齐），
 * 元数据（eventId / correlationId / causationId / timestamp）以列为准，
 * payload JSON 仅承载业务属性（见 {@link EventPayloadSerializer}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public class JpaEventStore implements EventStore {

    private static final String ENTITY = "StoredEventEntity";

    private final EntityManager entityManager;
    private final EventPayloadSerializer serializer;

    public JpaEventStore(EntityManager entityManager) {
        this(entityManager, new EventPayloadSerializer());
    }

    public JpaEventStore(EntityManager entityManager, EventPayloadSerializer serializer) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
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
        EntityTransaction tx = entityManager.getTransaction();
        try {
            tx.begin();
            long actualVersion = currentVersion(aggregateType, aggregateId.asString());
            if (actualVersion != expectedVersion) {
                throw new AggregateVersionConflictException(
                        aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
            }
            long position = maxPosition();
            long version = expectedVersion;
            for (DomainEvent<?> event : events) {
                version++;
                event.setAggregateVersion(new AggregateVersion(version));
                StoredEventEntity entity = new StoredEventEntity();
                entity.setAggregateType(aggregateType);
                entity.setAggregateId(aggregateId.asString());
                entity.setVersion(version);
                entity.setPosition(++position);
                entity.setEventType(event.getClass().getName());
                entity.setEventId(event.getEventId().asString());
                entity.setCorrelationId(event.getCorrelationId() == null ? null : event.getCorrelationId().asString());
                entity.setCausationId(event.getCausationId() == null ? null : event.getCausationId().asString());
                entity.setPayload(serializer.serialize(event));
                entity.setTimestamp(Instant.now());
                entityManager.persist(entity);
            }
            entityManager.flush();
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        EntityTransaction tx = entityManager.getTransaction();
        try {
            tx.begin();
            List<StoredEvent> result = entityManager.createQuery(
                            "select e from " + ENTITY + " e where e.aggregateType = :aggregateType"
                                    + " and e.aggregateId = :aggregateId order by e.version asc",
                            StoredEventEntity.class)
                    .setParameter("aggregateType", aggregateType)
                    .setParameter("aggregateId", aggregateId.asString())
                    .getResultList()
                    .stream()
                    .map(this::toStoredEvent)
                    .collect(java.util.stream.Collectors.toList());
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        EntityTransaction tx = entityManager.getTransaction();
        try {
            tx.begin();
            List<StoredEvent> result = entityManager.createQuery(
                            "select e from " + ENTITY + " e where e.aggregateType = :aggregateType"
                                    + " and e.aggregateId = :aggregateId"
                                    + " and e.version between :fromVersion and :toVersion"
                                    + " order by e.version asc",
                            StoredEventEntity.class)
                    .setParameter("aggregateType", aggregateType)
                    .setParameter("aggregateId", aggregateId.asString())
                    .setParameter("fromVersion", fromVersion)
                    .setParameter("toVersion", toVersion)
                    .getResultList()
                    .stream()
                    .map(this::toStoredEvent)
                    .collect(java.util.stream.Collectors.toList());
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        EntityTransaction tx = entityManager.getTransaction();
        try {
            tx.begin();
            List<StoredEvent> result = entityManager.createQuery(
                            "select e from " + ENTITY + " e where e.position >= :fromPosition order by e.position asc",
                            StoredEventEntity.class)
                    .setParameter("fromPosition", fromPosition)
                    .setMaxResults(limit)
                    .getResultList()
                    .stream()
                    .map(this::toStoredEvent)
                    .collect(java.util.stream.Collectors.toList());
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    private long currentVersion(String aggregateType, String aggregateId) {
        TypedQuery<Long> query = entityManager.createQuery(
                "select count(e) from " + ENTITY + " e where e.aggregateType = :aggregateType"
                        + " and e.aggregateId = :aggregateId", Long.class);
        return query.setParameter("aggregateType", aggregateType)
                .setParameter("aggregateId", aggregateId)
                .getSingleResult();
    }

    private long maxPosition() {
        // PESSIMISTIC_WRITE 行锁序列化并发 append 的全局 position 分配
        //（回填自 3.0.x 7263653c）。取最大 position 行加锁（避免聚合查询，
        // H2 不支持 grouped select 的 FOR UPDATE）；空表无行可锁时由
        // uk_position 唯一约束兜底并发冲突。
        List<Long> maxRows = entityManager.createQuery(
                "select e.position from " + ENTITY + " e order by e.position desc", Long.class)
                .setMaxResults(1)
                .setLockMode(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return maxRows.isEmpty() ? 0L : maxRows.get(0);
    }

    private StoredEvent toStoredEvent(StoredEventEntity entity) {
        DomainEvent<?> payload = serializer.deserialize(entity.getPayload(), resolveEventType(entity.getEventType()));
        return new StoredEvent(
                EventId.valueOf(entity.getEventId()),
                entity.getAggregateType(),
                new StringAggregateRootId(entity.getAggregateId()),
                entity.getVersion(),
                entity.getPosition(),
                ZonedDateTime.ofInstant(entity.getTimestamp(), ZoneOffset.UTC),
                payload,
                EventId.valueOf(entity.getCorrelationId()),
                EventId.valueOf(entity.getCausationId()));
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
