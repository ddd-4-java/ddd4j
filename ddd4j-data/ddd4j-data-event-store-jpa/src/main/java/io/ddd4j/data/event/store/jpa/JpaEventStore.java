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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于 JPA 的 {@link EventStore} 实现（CQRS 写侧持久化）。
 *
 * <p>通过 {@link JpaStoredEventRepository} 封装持久化原语，本类专注于
 * EventStore SPI 语义：乐观锁校验、事件序列化/反序列化、事务边界管理。
 *
 * <h2>事务管理</h2>
 * <p>默认构造器使用编程式事务管理（{@link EntityTransaction}），在 {@code append}
 * 方法内开启事务，冲突或异常时整体回滚。调用方无需（也不应）在外层包裹事务。
 * {@code read} / {@code readAll} 同样在本方法内开启只读事务，保证读隔离性。
 * 通过 {@link #participating(EntityManager)} 显式创建的实例只参与调用方已开启的
 * RESOURCE_LOCAL 事务，不负责提交、回滚或清理持久化上下文。
 *
 * <h3>payload 序列化</h3>
 * <p>事件载荷通过 {@link EventPayloadSerializer} 序列化为 JSON 文本，
 * 读取时按 {@code event_type} 还原事件类型。事件类不存在时抛出
 * {@link IllegalStateException}，不会静默转换为 {@code Map}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class JpaEventStore implements EventStore {

    /** JPQL \u8bfb\u5199\u4e8b\u4ef6\u8bb0\u5f55\u65f6\u4f7f\u7528\u7684\u5b9e\u4f53\u540d\u79f0\u3002 */
    private static final String ENTITY = "StoredEventEntity";

    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private final EntityManager entityManager;
    /** \u6309\u805a\u5408\u53ca\u4f4d\u7f6e\u67e5\u8be2 JPA \u4e8b\u4ef6\u8bb0\u5f55\u7684\u4ed3\u50a8\u3002 */
    private final JpaStoredEventRepository repository;
    /** \u5c06\u9886\u57df\u4e8b\u4ef6\u8d1f\u8f7d\u5199\u4e3a\u5b58\u50a8\u683c\u5f0f\u5e76\u4ece\u5b58\u50a8\u683c\u5f0f\u6062\u590d\u7684\u5e8f\u5217\u5316\u5668\u3002 */
    private final EventPayloadSerializer serializer;
    /** \u5f53\u524d JPA \u4e8b\u4ef6\u5b58\u50a8\u91c7\u7528\u7684\u4e8b\u52a1\u8fb9\u754c\u3002 */
    private final TransactionMode transactionMode;
    /** \u6258\u7ba1\u4e8b\u52a1\u5199\u5165\u5931\u8d25\u65f6\u901a\u77e5\u4e8b\u52a1\u7ba1\u7406\u5668\u6807\u8bb0\u56de\u6eda\u7684\u52a8\u4f5c\u3002 */
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

    /**
     * \u521b\u5efa\u4f7f\u7528\u6307\u5b9a\u8f7d\u8377\u5e8f\u5217\u5316\u5668\u7684 JPA \u4e8b\u4ef6\u5b58\u50a8\u3002
     * @param entityManager JPA \u5b9e\u4f53\u7ba1\u7406\u5668
     * @param repository \u4e8b\u4ef6\u4ed3\u50a8\u5b9e\u73b0
     * @param serializer \u4e8b\u4ef6\u8f7d\u8377\u5e8f\u5217\u5316\u5668
     */
    public JpaEventStore(EntityManager entityManager, JpaStoredEventRepository repository,
                         EventPayloadSerializer serializer) {
        this(entityManager, repository, serializer, TransactionMode.INDEPENDENT);
    }

    /**
     * \u6309\u9009\u5b9a\u4e8b\u52a1\u6a21\u5f0f\u88c5\u914d JPA \u4e8b\u4ef6\u5b58\u50a8\u3002
     * @param entityManager \u6267\u884c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668
     * @param repository \u8bfb\u53d6\u5df2\u6301\u4e45\u5316\u4e8b\u4ef6\u7684\u4ed3\u50a8
     * @param serializer \u4e8b\u4ef6\u8d1f\u8f7d\u7684\u5e8f\u5217\u5316\u5668
     * @param transactionMode \u5199\u5165\u4f7f\u7528\u7684\u4e8b\u52a1\u6a21\u5f0f
     */
    private JpaEventStore(EntityManager entityManager, JpaStoredEventRepository repository,
                          EventPayloadSerializer serializer, TransactionMode transactionMode) {
        this(entityManager, repository, serializer, transactionMode, null);
    }

    /**
     * \u4e3a JPA \u5b58\u50a8\u88c5\u914d\u8bfb\u5199\u7ec4\u4ef6\u548c\u6258\u7ba1\u4e8b\u52a1\u56de\u6eda\u6807\u8bb0\u3002
     * @param entityManager \u6267\u884c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668
     * @param repository \u8bfb\u53d6\u5df2\u6301\u4e45\u5316\u4e8b\u4ef6\u7684\u4ed3\u50a8
     * @param serializer \u4e8b\u4ef6\u8d1f\u8f7d\u7684\u5e8f\u5217\u5316\u5668
     * @param transactionMode \u5199\u5165\u4f7f\u7528\u7684\u4e8b\u52a1\u6a21\u5f0f
     * @param markRollbackOnly \u901a\u77e5\u6258\u7ba1\u4e8b\u52a1\u6807\u8bb0\u56de\u6eda\u7684\u52a8\u4f5c
     */
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

    /**
     * 向指定聚合的事件流追加这一批领域事件。
     *
     * <p>\u4e50\u89c2\u9501\uff1a\u5148\u67e5\u8be2\u5f53\u524d\u6d41\u6700\u5927\u7248\u672c\uff0c\u4e0e {@code expectedVersion} \u4e0d\u4e00\u81f4\u5373\u629b
     *  {@link IllegalStateException}\u3002\u540c\u4e00\u805a\u5408\u7684 append \u64cd\u4f5c\u5728\u540c\u4e00\u4e8b\u52a1\u5185\u5b8c\u6210\uff0c
     *  \u51b2\u7a81\u65f6\u6574\u4f53\u56de\u6eda\uff0c\u4e0d\u7559\u534a\u622a\u6d41\u3002
     *
     *  <p>\u4e8b\u52a1\u8fb9\u754c\uff1a\u9ed8\u8ba4\u6a21\u5f0f\u5728\u672c\u65b9\u6cd5\u5185\u5f00\u542f\u7f16\u7a0b\u5f0f\u4e8b\u52a1\uff1b\u663e\u5f0f\u53c2\u4e0e\u6a21\u5f0f\u590d\u7528\u8c03\u7528\u65b9\u4e8b\u52a1\u3002
     *
     *  <p><b>\u6279\u91cf\u63d2\u5165</b>
     *  <p>\u5faa\u73af\u6784\u9020 entity \u540e\uff0c\u5148 {@link EntityManager#flush()} \u89e6\u53d1 Hibernate \u6279\u91cf INSERT\uff1b
     *  \u4ec5\u9ed8\u8ba4\u72ec\u7acb\u6a21\u5f0f\u518d\u901a\u8fc7 {@link EntityManager#clear()} \u6e05\u7a7a persistence context\u3002
     *  \u9ed8\u8ba4\u72ec\u7acb\u6a21\u5f0f\u7684\u987a\u5e8f\u5f88\u5173\u952e\uff1a\u5148 flush \u518d clear \u624d\u80fd\u8ba9 Hibernate \u628a\u6240\u6709 INSERT \u5408\u5e76\u4e3a\u6279\u91cf SQL\u3002
     *
     *  <p>\u6279\u91cf\u63d2\u5165\u9700\u914d\u5408 Hibernate \u914d\u7f6e\u751f\u6548\uff1a
     *  <pre>{@code
     *  spring.jpa.properties.hibernate.jdbc.batch_size=50
     *  spring.jpa.properties.hibernate.order_inserts=true
     *  spring.jpa.properties.hibernate.order_updates=true
     *  }</pre>
     *
     *  <p>\u672a\u914d\u7f6e batch_size \u65f6\uff0c\u6bcf\u6b21 flush \u4ecd\u4f1a\u53d1\u51fa\u5355\u6761 INSERT\uff08\u884c\u4e3a\u6b63\u786e\uff0c\u4f46\u65e0\u6027\u80fd\u63d0\u5347\uff09\u3002
     *
     * <p>JPA 实现调用 <code>events.isEmpty()</code>。</p>
     *
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合 ID
     * @param events 要追加的事件（非空列表）
     * @param expectedVersion 期望的当前版本号（乐观锁，空流为 0）
     */
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
            if (transactionMode == TransactionMode.INDEPENDENT) {
                entityManager.clear();
            }

            long currentVersion = repository.findCurrentVersion(aggregateType, aggregateId.asString());
            if (currentVersion != expectedVersion) {
                throw new AggregateVersionConflictException(
                        aggregateType, aggregateId.asString(), expectedVersion, currentVersion);
            }

            // 锁定已有的最后位置行，串行化跨聚合的全局 position 分配。
            maxPosition();
            long version = expectedVersion;
            for (DomainEvent<?> event : events) {
                StoredEventEntity entity = new StoredEventEntity();
                entity.setAggregateType(aggregateType);
                entity.setAggregateId(aggregateId.asString());
                entity.setVersion(++version);
                entity.setPosition(repository.nextPosition());
                entity.setEventType(event.getClass().getName());
                entity.setEventId(event.getEventId().asString());
                entity.setCorrelationId(event.getCorrelationId() == null ? null : event.getCorrelationId().asString());
                entity.setCausationId(event.getCausationId() == null ? null : event.getCausationId().asString());
                entity.setPayload(serializer.serialize(event));
                entity.setTimestamp(event.getEventTimestamp().toInstant());
                repository.save(entity);
            }

            // 触发 Hibernate 批量 INSERT：先 flush 把所有未刷盘的 SQL 发到 DB；
            // 仅独立模式再 clear 释放 persistence context 中的 entity（避免 OOM）。
            entityManager.flush();
            if (transactionMode == TransactionMode.INDEPENDENT) {
                entityManager.clear();
            }
            return null;
        });
    }

    /**
     * 按聚合查询其事件流并还原已存储的事件。
     *
     * <p>\u6309\u7248\u672c\u5347\u5e8f\u8bfb\u53d6\u6307\u5b9a\u805a\u5408\u7684\u5168\u90e8\u4e8b\u4ef6\u3002
     *  \u4e8b\u4ef6\u8f7d\u8377\u901a\u8fc7 {@link EventPayloadSerializer} \u53cd\u5e8f\u5217\u5316\uff1b\u4e8b\u4ef6\u7c7b\u578b\u65e0\u6cd5\u8fd8\u539f\u65f6\u629b\u51fa {@link IllegalStateException}\u3002
     *
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合 ID
     * @return 按版本升序的持久化事件；无事件时为空列表
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return executeInTransaction(() -> repository.findByAggregateTypeAndAggregateIdOrderByVersionAsc(
                        aggregateType, aggregateId.asString())
                .stream()
                .map(this::toStoredEvent)
                .toList());
    }

    /**
     * 按聚合查询其事件流并还原已存储的事件。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合 ID
     * @param fromVersion 起始版本号（含）
     * @param toVersion 结束版本号（含）
     * @return 版本区间内的持久化事件，按版本升序
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        return executeInTransaction(() -> repository
                .findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
                        aggregateType, aggregateId.asString(), fromVersion, toVersion)
                .stream()
                .map(this::toStoredEvent)
                .toList());
    }

    /**
     * 按全局位置读取用于投影或重放的存储事件。
     *
     * <p>\u6309\u5168\u5c40 position \u5347\u5e8f\u5206\u9875\u8bfb\u53d6\u4e8b\u4ef6\u3002
     *
     * <p>满足 <code>limit &lt;= 0</code> 时抛出 <code>IllegalArgumentException</code>。</p>
     *
     * @param fromPosition 起始 position（含）
     * @param limit 最大读取数量
     * @return position 升序的持久化事件
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return executeInTransaction(() -> repository
                .findByPositionGreaterThanEqualOrderByPositionAsc(fromPosition, limit)
                .stream()
                .map(this::toStoredEvent)
                .toList());
    }

    /**
     * \u6839\u636e\u914d\u7f6e\u9009\u62e9\u72ec\u7acb\u3001\u53c2\u4e0e\u5f0f\u6216\u8c03\u7528\u65b9\u6258\u7ba1\u7684\u4e8b\u52a1\u6267\u884c\u8def\u5f84\u3002
     * @param <T> \u4e8b\u52a1\u64cd\u4f5c\u7684\u8fd4\u56de\u7c7b\u578b
     * @param operation \u5728\u9009\u5b9a\u4e8b\u52a1\u8fb9\u754c\u5185\u8fd0\u884c\u7684\u64cd\u4f5c
     * @return \u4e8b\u52a1\u64cd\u4f5c\u7ed3\u679c
     */
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

    /**
     * \u5728\u5df2\u5f00\u542f\u7684\u8d44\u6e90\u672c\u5730\u4e8b\u52a1\u5185\u6267\u884c\u4e8b\u4ef6\u5199\u5165\uff0c\u5931\u8d25\u65f6\u6807\u8bb0\u56de\u6eda\u3002
     * @param <T> \u5199\u5165\u64cd\u4f5c\u7684\u8fd4\u56de\u7c7b\u578b
     * @param operation \u5728\u9009\u5b9a\u4e8b\u52a1\u8fb9\u754c\u5185\u8fd0\u884c\u7684\u64cd\u4f5c
     * @return \u4e8b\u4ef6\u5199\u5165\u64cd\u4f5c\u7684\u8fd4\u56de\u7ed3\u679c
     * @throws IllegalStateException \u4e0d\u5b58\u5728\u53ef\u53c2\u4e0e\u7684\u8c03\u7528\u65b9\u4e8b\u52a1
     */
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

    /**
     * \u4f7f\u7528\u5df2\u52a0\u5165\u8c03\u7528\u65b9\u4e8b\u52a1\u7684 EntityManager \u6267\u884c\u5199\u5165\u3002
     * @param <T> \u5199\u5165\u64cd\u4f5c\u7684\u8fd4\u56de\u7c7b\u578b
     * @param operation \u5728\u9009\u5b9a\u4e8b\u52a1\u8fb9\u754c\u5185\u8fd0\u884c\u7684\u64cd\u4f5c
     * @return \u4e8b\u4ef6\u5199\u5165\u64cd\u4f5c\u7684\u8fd4\u56de\u7ed3\u679c
     * @throws IllegalStateException \u5b9e\u4f53\u7ba1\u7406\u5668\u5c1a\u672a\u52a0\u5165\u8c03\u7528\u65b9\u4e8b\u52a1
     */
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

    /**
     * \u6267\u884c\u56de\u6eda\u6807\u8bb0\uff0c\u5e76\u5c06\u6807\u8bb0\u5931\u8d25\u4f5c\u4e3a\u539f\u64cd\u4f5c\u5f02\u5e38\u7684\u6291\u5236\u5f02\u5e38\u4fdd\u7559\u3002
     * @param failure \u4e1a\u52a1\u64cd\u4f5c\u539f\u5148\u629b\u51fa\u7684\u5f02\u5e38
     * @param rollbackMarker \u8bf7\u6c42\u56de\u6eda\u5f53\u524d\u4e8b\u52a1\u7684\u52a8\u4f5c
     */
    private void preserveRollbackMarkingFailure(Throwable failure, Runnable rollbackMarker) {
        try {
            rollbackMarker.run();
        } catch (RuntimeException | Error markingFailure) {
            if (markingFailure != failure) {
                failure.addSuppressed(markingFailure);
            }
        }
    }

    /**
     * \u67e5\u8be2\u4e8b\u4ef6\u8868\u4e2d\u5df2\u7ecf\u5206\u914d\u7684\u6700\u5927\u5168\u5c40\u4f4d\u7f6e\u3002
     * @return \u5f53\u524d\u6700\u5927\u5168\u5c40\u4f4d\u7f6e\uff0c\u6ca1\u6709\u8bb0\u5f55\u65f6\u4e3a\u96f6
     */
    private long maxPosition() {
        List<Long> maxRows = entityManager.createQuery(
                        "select e.position from " + ENTITY + " e order by e.position desc", Long.class)
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return maxRows.isEmpty() ? 0L : maxRows.get(0);
    }

    /**
     * 将持久化实体转换为 core {@link StoredEvent}。
     *
     * <p>事件类型通过显式存储的 class name 还原，payload 通过强类型序列化器反序列化。
     *
     * @param entity 持久化实体
     * @return 重建的存储事件
     */
    private StoredEvent toStoredEvent(StoredEventEntity entity) {
        DomainEvent<?> event = serializer.deserialize(entity.getPayload(), resolveEventType(entity.getEventType()));
        return new StoredEvent(
                EventId.valueOf(entity.getEventId()),
                entity.getAggregateType(),
                new StringAggregateRootId(entity.getAggregateId()),
                entity.getVersion(),
                entity.getPosition(),
                ZonedDateTime.ofInstant(entity.getTimestamp(), ZoneOffset.UTC),
                event,
                EventId.valueOf(entity.getCorrelationId()),
                EventId.valueOf(entity.getCausationId()));
    }

    /**
     * 解析存储的事件类。
     *
     * <p>读取存储的事件类；类不存在时失败，不回退为无类型数据。
     *
     * @param eventType 事件类型全限定名
     * @return 领域事件类型
     * @throws IllegalStateException 事件类不存在
     */
    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unknown event type: " + eventType, exception);
        }
    }

    /** \u6807\u8bc6 JPA \u4e8b\u4ef6\u5b58\u50a8\u4f7f\u7528\u72ec\u7acb\u4e8b\u52a1\u3001\u8c03\u7528\u65b9\u4e8b\u52a1\u6216\u6258\u7ba1\u4e8b\u52a1\u7684\u65b9\u5f0f\u3002 */
    private enum TransactionMode {
        /** \u4e8b\u4ef6\u5b58\u50a8\u81ea\u884c\u5f00\u542f\u5e76\u63d0\u4ea4\u4e8b\u52a1\u3002 */
        INDEPENDENT,
        /** \u4e8b\u4ef6\u5199\u5165\u53c2\u4e0e\u5df2\u5b58\u5728\u7684\u8c03\u7528\u65b9\u4e8b\u52a1\u3002 */
        PARTICIPATING,
        /** \u7531\u5916\u90e8\u4e8b\u52a1\u8bbe\u65bd\u7ba1\u7406\u4e8b\u52a1\u8fb9\u754c\u3002 */
        MANAGED
    }

    /** \u5c06\u4e8b\u4ef6\u5b58\u50a8\u4e2d\u7684\u5b57\u7b26\u4e32\u6807\u8bc6\u9002\u914d\u4e3a\u805a\u5408\u6839\u6807\u8bc6\u3002 */
    private final static class StringAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u4ece\u6301\u4e45\u5316\u8bb0\u5f55\u8bfb\u53d6\u7684\u805a\u5408\u6807\u8bc6\u539f\u503c\u3002 */
        private final String value;

        /** \u5b57\u7b26\u4e32\u683c\u5f0f\u7684\u805a\u5408\u6807\u8bc6\u7c7b\u578b\u3002 */
        private static final StringEntityType TYPE = new StringEntityType("String");

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 不含类型标记的业务标识
         */
        @Override
        @JsonValue
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>此实现调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 包含类型标记的标识
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u628a\u6301\u4e45\u5316\u6807\u8bc6\u6062\u590d\u6210\u5b57\u7b26\u4e32\u805a\u5408\u6839\u6807\u8bc6\u3002
         * @param value \u5b58\u50a8\u7684\u6807\u8bc6\u539f\u503c
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

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
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

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "StringAggregateRootId[value=" + value + "]";
        }
    }
}
