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
package io.ddd4j.data.event.store.jpa;

import com.fasterxml.jackson.databind.json.JsonMapper;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于 JPA 2.2（javax.persistence）的 {@link EventStore} 适配器。
 *
 * <h3>事务管理</h3>
 * <p>默认构造器使用程序化事务（{@link EntityTransaction}）：{@code append} 在方法内开启事务，
 * 冲突或异常时整体回滚；{@code read} / {@code readAll} 同样包裹只读事务保证读隔离。
 * 调用方无需（也不应）在外层包裹事务。
 * 通过 {@link #participating(EntityManager)} 显式创建的实例只参与调用方已开启的
 * RESOURCE_LOCAL 事务，不负责提交、回滚或清理持久化上下文。
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
    private final JpaStoredEventRepository repository;
    private final EventPayloadSerializer serializer;
    private final TransactionMode transactionMode;
    private final Runnable markRollbackOnly;

    /**
     * 创建 JPA 事件存储。
     *
     * <p>使用默认的 {@link JpaStoredEventRepositoryImpl} 作为仓储实现。
     *
     * @param entityManager JPA 实体管理器（由调用方管理生命周期）
     * @throws NullPointerException entityManager 为 null 时抛出
     */
    public JpaEventStore(EntityManager entityManager) {
        this(entityManager, new JpaStoredEventRepositoryImpl(entityManager),
                new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    /**
     * 创建 JPA 事件存储（自定义仓储）。
     *
     * @param entityManager JPA 实体管理器
     * @param repository    事件仓储实现
     * @throws NullPointerException 任一参数为 null 时抛出
     */
    public JpaEventStore(EntityManager entityManager, JpaStoredEventRepository repository) {
        this(entityManager, repository, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    public JpaEventStore(EntityManager entityManager, JpaStoredEventRepository repository,
                         EventPayloadSerializer serializer) {
        this(entityManager, repository, serializer, TransactionMode.INDEPENDENT);
    }

    private JpaEventStore(EntityManager entityManager, JpaStoredEventRepository repository,
                          EventPayloadSerializer serializer, TransactionMode transactionMode) {
        this(entityManager, repository, serializer, transactionMode, null);
    }

    private JpaEventStore(EntityManager entityManager, JpaStoredEventRepository repository,
                          EventPayloadSerializer serializer, TransactionMode transactionMode,
                          Runnable markRollbackOnly) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
        this.transactionMode = Objects.requireNonNull(transactionMode, "transactionMode must not be null");
        this.markRollbackOnly = transactionMode == TransactionMode.MANAGED
                ? Objects.requireNonNull(markRollbackOnly, "markRollbackOnly must not be null") : markRollbackOnly;
    }

    /**
     * 创建参与调用方 RESOURCE_LOCAL 事务的事件存储。
     *
     * @param entityManager 调用方事务绑定的实体管理器
     * @return 仅参与当前活动事务的事件存储
     */
    public static JpaEventStore participating(EntityManager entityManager) {
        return participating(entityManager, new JpaStoredEventRepositoryImpl(entityManager),
                new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    /**
     * 创建参与调用方 RESOURCE_LOCAL 事务的事件存储（自定义仓储与序列化器）。
     *
     * @param entityManager 调用方事务绑定的实体管理器
     * @param repository    使用同一事务资源的事件仓储
     * @param serializer    事件载荷序列化器
     * @return 仅参与当前活动事务的事件存储
     */
    public static JpaEventStore participating(EntityManager entityManager,
                                               JpaStoredEventRepository repository,
                                               EventPayloadSerializer serializer) {
        return new JpaEventStore(entityManager, repository, serializer, TransactionMode.PARTICIPATING);
    }

    /**
     * 创建参与容器管理事务的事件存储。
     *
     * <p>实例构造不要求事务已经激活；每次操作通过
     * {@link EntityManager#isJoinedToTransaction()} 校验当前实体管理器已经加入事务。
     *
     * @param entityManager    调用方事务绑定的实体管理器
     * @param markRollbackOnly 标记当前容器事务只能回滚的回调
     * @return 仅参与当前容器管理事务的事件存储
     */
    public static JpaEventStore participatingManaged(EntityManager entityManager, Runnable markRollbackOnly) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(markRollbackOnly, "markRollbackOnly must not be null");
        return participatingManaged(entityManager, new JpaStoredEventRepositoryImpl(entityManager),
                new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()), markRollbackOnly);
    }

    /**
     * 创建参与容器管理事务的事件存储（自定义仓储与序列化器）。
     *
     * @param entityManager    调用方事务绑定的实体管理器
     * @param repository       使用同一事务资源的事件仓储
     * @param serializer       事件载荷序列化器
     * @param markRollbackOnly 标记当前容器事务只能回滚的回调
     * @return 仅参与当前容器管理事务的事件存储
     */
    public static JpaEventStore participatingManaged(EntityManager entityManager,
                                                      JpaStoredEventRepository repository,
                                                      EventPayloadSerializer serializer,
                                                      Runnable markRollbackOnly) {
        Objects.requireNonNull(entityManager, "entityManager must not be null");
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(serializer, "serializer must not be null");
        Objects.requireNonNull(markRollbackOnly, "markRollbackOnly must not be null");
        return new JpaEventStore(entityManager, repository, serializer, TransactionMode.MANAGED,
                markRollbackOnly);
    }

    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty() && transactionMode == TransactionMode.INDEPENDENT) {
            return;
        }
        executeInTransaction(() -> {
            if (events.isEmpty()) {
                return null;
            }
            long actualVersion = repository.findCurrentVersion(aggregateType, aggregateId.asString());
            if (actualVersion != expectedVersion) {
                throw new AggregateVersionConflictException(
                        aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
            }
            // 保留既有位置行锁，实际位置分配交给可替换的仓储端口。
            maxPosition();
            long version = expectedVersion;
            for (DomainEvent<?> event : events) {
                version++;
                StoredEventEntity entity = new StoredEventEntity();
                entity.setAggregateType(aggregateType);
                entity.setAggregateId(aggregateId.asString());
                entity.setVersion(version);
                entity.setPosition(repository.nextPosition());
                entity.setEventType(event.getClass().getName());
                entity.setEventId(event.getEventId().asString());
                entity.setCorrelationId(event.getCorrelationId() == null ? null : event.getCorrelationId().asString());
                entity.setCausationId(event.getCausationId() == null ? null : event.getCausationId().asString());
                entity.setPayload(serializer.serialize(event));
                entity.setTimestamp(event.getEventTimestamp().toInstant());
                repository.save(entity);
            }
            entityManager.flush();
            return null;
        });
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return executeInTransaction(() -> repository.findByAggregateTypeAndAggregateIdOrderByVersionAsc(
                            aggregateType, aggregateId.asString())
                    .stream()
                    .map(this::toStoredEvent)
                    .collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        return executeInTransaction(() -> repository
                .findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
                            aggregateType, aggregateId.asString(), fromVersion, toVersion)
                    .stream()
                    .map(this::toStoredEvent)
                    .collect(java.util.stream.Collectors.toList()));
    }

    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return executeInTransaction(() -> repository
                .findByPositionGreaterThanEqualOrderByPositionAsc(fromPosition, limit)
                .stream()
                .map(this::toStoredEvent)
                .collect(java.util.stream.Collectors.toList()));
    }

    private <T> T executeInTransaction(Supplier<T> operation) {
        if (transactionMode == TransactionMode.PARTICIPATING) {
            return executeParticipating(operation);
        }
        if (transactionMode == TransactionMode.MANAGED) {
            return executeManaged(operation);
        }

        EntityTransaction tx = entityManager.getTransaction();
        if (tx.isActive()) {
            throw new IllegalStateException("JpaEventStore requires an EntityManager without an active transaction");
        }
        try {
            tx.begin();
            T result = operation.get();
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    private <T> T executeParticipating(Supplier<T> operation) {
        EntityTransaction tx = entityManager.getTransaction();
        if (!tx.isActive()) {
            throw new IllegalStateException("An active caller transaction is required");
        }
        try {
            return operation.get();
        } catch (RuntimeException | Error failure) {
            preserveRollbackMarkingFailure(failure, tx::setRollbackOnly);
            throw failure;
        }
    }

    private <T> T executeManaged(Supplier<T> operation) {
        if (!entityManager.isJoinedToTransaction()) {
            throw new IllegalStateException("An EntityManager joined to the caller transaction is required");
        }
        try {
            return operation.get();
        } catch (RuntimeException | Error failure) {
            preserveRollbackMarkingFailure(failure, markRollbackOnly);
            throw failure;
        }
    }

    private void preserveRollbackMarkingFailure(Throwable failure, Runnable rollbackMarker) {
        try {
            rollbackMarker.run();
        } catch (RuntimeException | Error markingFailure) {
            if (markingFailure != failure) {
                failure.addSuppressed(markingFailure);
            }
        }
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

    private enum TransactionMode {
        INDEPENDENT,
        PARTICIPATING,
        MANAGED
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
