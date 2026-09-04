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

import com.fasterxml.jackson.annotation.JsonValue;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于 JPA 的 {@link EventStore} 实现（CQRS 写侧持久化）。
 *
 * <p>通过 {@link JpaStoredEventRepository} 封装持久化原语，本类专注于
 * EventStore SPI 语义：乐观锁校验、事件序列化/反序列化、事务边界管理。
 *
 * <h3>事务管理</h3>
 * <p>本实现使用编程式事务管理（{@link EntityTransaction}），在 {@code append}
 * 方法内开启事务，冲突或异常时整体回滚。调用方无需（也不应）在外层包裹事务。
 * {@code read} / {@code readAll} 同样在本方法内开启只读事务，保证读隔离性。
 *
 * <h3>payload 序列化</h3>
 * <p>事件载荷通过 {@link JsonKit#toJson} 序列化为 JSON 文本存储，
 * 读取时通过 {@link JsonKit#toObject} 按 {@code event_type} 反序列化。
 * 若事件类已被删除或重命名（{@code Class.forName} 失败），回退为 {@code Map}，
 * 此时丢失类型信息，javadoc 已说明此限制。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class JpaEventStore implements EventStore {

    private final EntityManager entityManager;
    private final JpaStoredEventRepository repository;
    private final EventPayloadSerializer serializer;

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
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁：先查询当前流最大版本，与 {@code expectedVersion} 不一致即抛
     * {@link IllegalStateException}。同一聚合的 append 操作在同一事务内完成，
     * 冲突时整体回滚，不留半截流。
     *
     * <p>事务边界：本方法内开启编程式事务，调用方无需额外包裹。
     *
     * <h3>批量插入</h3>
     * <p>循环构造 entity 后，先 {@link EntityManager#flush()} 触发 Hibernate 批量 INSERT，
     * 再 {@link EntityManager#clear()} 清空 persistence context。
     * 顺序很关键：先 flush 再 clear 才能让 Hibernate 把所有 INSERT 合并为批量 SQL。
     *
     * <p>批量插入需配合 Hibernate 配置生效：
     * <pre>{@code
     * spring.jpa.properties.hibernate.jdbc.batch_size=50
     * spring.jpa.properties.hibernate.order_inserts=true
     * spring.jpa.properties.hibernate.order_updates=true
     * }</pre>
     *
     * <p>未配置 batch_size 时，每次 flush 仍会发出单条 INSERT（行为正确，但无性能提升）。
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

        EntityTransaction tx = entityManager.getTransaction();
        try {
            tx.begin();
            entityManager.clear();

            long currentVersion = repository.findCurrentVersion(aggregateType, aggregateId.asString());
            if (currentVersion != expectedVersion) {
                throw new AggregateVersionConflictException(
                        aggregateType, aggregateId.asString(), expectedVersion, currentVersion);
            }

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

            // 触发 Hibernate 批量 INSERT：先 flush 把所有未刷盘的 SQL 发到 DB，
            // 再 clear 释放 persistence context 中的 entity（避免 OOM）。
            entityManager.flush();
            entityManager.clear();

            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>按版本升序读取指定聚合的全部事件。
     * 事件载荷通过 {@link JsonKit} 反序列化，类型无法还原时回退为 {@code Map}。
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            List<StoredEvent> result = repository.findByAggregateTypeAndAggregateIdOrderByVersionAsc(
                            aggregateType, aggregateId.asString())
                    .stream()
                    .map(this::toStoredEvent)
                    .toList();
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
        tx.begin();
        try {
            List<StoredEvent> result = repository.findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
                            aggregateType, aggregateId.asString(), fromVersion, toVersion)
                    .stream()
                    .map(this::toStoredEvent)
                    .toList();
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
     * {@inheritDoc}
     *
     * <p>按全局 position 升序分页读取事件。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        try {
            List<StoredEvent> result = repository.findByPositionGreaterThanEqualOrderByPositionAsc(fromPosition, limit)
                    .stream()
                    .map(this::toStoredEvent)
                    .toList();
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
     * 反序列化事件载荷。
     *
     * <p>优先尝试按 {@code eventType} 还原为强类型对象；
     * 若类不存在，回退为 {@code Map}（丢失类型信息）。
     *
     * @param payload   JSON 文本
     * @param eventType 事件类型全限定名
     * @return 反序列化后的事件对象或 Map
     */
    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unknown event type: " + eventType, exception);
        }
    }

    private record StringAggregateRootId(String value) implements AggregateRootId {

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
