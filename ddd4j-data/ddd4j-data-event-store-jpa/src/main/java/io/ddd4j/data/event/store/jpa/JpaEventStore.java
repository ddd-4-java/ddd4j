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

import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.kit.lang.JsonKit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * {@code read} / {@code readAll} 为只读操作，不主动管理事务。
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

    /**
     * 创建 JPA 事件存储。
     *
     * <p>使用默认的 {@link JpaStoredEventRepositoryImpl} 作为仓储实现。
     *
     * @param entityManager JPA 实体管理器（由调用方管理生命周期）
     * @throws NullPointerException entityManager 为 null 时抛出
     */
    public JpaEventStore(EntityManager entityManager) {
        this(entityManager, new JpaStoredEventRepositoryImpl(entityManager));
    }

    /**
     * 创建 JPA 事件存储（自定义仓储）。
     *
     * @param entityManager JPA 实体管理器
     * @param repository    事件仓储实现
     * @throws NullPointerException 任一参数为 null 时抛出
     */
    public JpaEventStore(EntityManager entityManager, JpaStoredEventRepository repository) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁：先查询当前流最大版本，与 {@code expectedVersion} 不一致即抛
     * {@link IllegalStateException}。同一聚合的 append 操作在同一事务内完成，
     * 冲突时整体回滚，不留半截流。
     *
     * <p>事务边界：本方法内开启编程式事务，调用方无需额外包裹。
     */
    @Override
    public void append(String aggregateId, List<Object> events, long expectedVersion) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            return;
        }

        EntityTransaction tx = entityManager.getTransaction();
        try {
            tx.begin();
            entityManager.clear();

            long currentVersion = repository.findCurrentVersion(aggregateId);
            if (currentVersion != expectedVersion) {
                throw new IllegalStateException(
                        "Version conflict: expected " + expectedVersion + " but was " + currentVersion);
            }

            Instant now = Instant.now();
            long version = expectedVersion;
            for (Object event : events) {
                StoredEventEntity entity = new StoredEventEntity();
                entity.setAggregateId(aggregateId);
                entity.setVersion(version);
                entity.setPosition(repository.nextPosition());
                entity.setEventType(event.getClass().getName());
                entity.setPayload(JsonKit.toJson(event));
                entity.setTimestamp(now);
                repository.save(entity);
                version++;
            }

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
    public List<StoredEvent> read(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return repository.findByAggregateIdOrderByVersionAsc(aggregateId)
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>按全局 position 升序分页读取事件。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return repository.findByPositionGreaterThanEqualOrderByPositionAsc(fromPosition, limit)
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    /**
     * 将持久化实体转换为 core {@link StoredEvent}。
     *
     * <p>事件类型通过 {@code Class.forName} 还原，payload 通过 {@link JsonKit#toObject}
     * 反序列化。若事件类不存在（被删除/重命名），回退为 {@code Map} 并记录日志。
     *
     * @param entity 持久化实体
     * @return 重建的存储事件
     */
    @SuppressWarnings("unchecked")
    private StoredEvent toStoredEvent(StoredEventEntity entity) {
        Object event = deserializePayload(entity.getPayload(), entity.getEventType());
        return new StoredEvent(
                entity.getAggregateId(),
                entity.getVersion(),
                event,
                entity.getPosition(),
                entity.getTimestamp());
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
    private Object deserializePayload(String payload, String eventType) {
        try {
            Class<?> eventClass = Class.forName(eventType);
            return JsonKit.toObject(payload, eventClass);
        } catch (ClassNotFoundException e) {
            // 事件类已被删除或重命名，回退为 Map
            return JsonKit.toMap(payload);
        }
    }
}
