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
package io.ddd4j.data.event.store.panache;

import io.ddd4j.core.cqrs.eventstore.EventDeserializer;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.kit.lang.JsonKit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Quarkus Hibernate ORM Panache 的 {@link EventStore} 实现（CQRS 写侧持久化）。
 *
 * <p>实现 3.0.x core SPI（{@link EventStore}），与 {@code ddd4j-data-event-store-jpa}
 * 共享同一张表（{@code DDD4J_EVENT_STORE}），保证跨运行时数据一致性。
 *
 * <p>实体层使用 Panache active record 模式（{@link PanacheStoredEventEntity}），
 * 查询层使用 {@link EntityManager} JPQL。Quarkus 运行时由容器管理事务，
 * 非容器环境（如测试）使用编程式事务管理（{@link EntityTransaction}）。
 *
 * <h3>事务管理</h3>
 * <p>{@code append} 方法内开启编程式事务，冲突或异常时整体回滚，不留半截流。
 * {@code read} / {@code readAll} 为只读操作，不主动管理事务。
 * Quarkus 运行时可叠加 {@code @Transactional} 声明式管理（本类不强制依赖 JTA）。
 *
 * <h3>序列化策略</h3>
 * <p>事件载荷通过 {@link JsonKit#toJson} 序列化为 JSON 文本存储，
 * 读取时通过 {@link EventDeserializer#deserialize} 按 {@code event_type} 反序列化。
 * 若事件类已被删除或重命名（{@code Class.forName} 失败），回退为 {@code Map}，
 * 此时丢失类型信息，javadoc 已说明此限制。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see PanacheStoredEventEntity
 * @since 3.0.0
 */
public class PanacheEventStore implements EventStore {

    private final EntityManager entityManager;

    /**
     * 创建 Panache 事件存储。
     *
     * @param entityManager JPA 实体管理器（由调用方管理生命周期）
     * @throws NullPointerException entityManager 为 null 时抛出
     */
    public PanacheEventStore(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>乐观锁：先查询当前流最大版本，与 {@code expectedVersion} 不一致即抛
     * {@link IllegalStateException}。同一聚合的 append 操作在同一事务内完成，
     * 冲突时整体回滚，不留半截流。
     *
     * <p>position 生成：每条事件经 {@link #nextPosition()} 以行锁分配，
     * 并发 append 在最大行上互斥串行化（详见该方法说明）。
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

            long currentVersion = findCurrentVersion(aggregateId);
            if (currentVersion != expectedVersion) {
                throw new IllegalStateException(
                        "Version conflict: expected " + expectedVersion + " but was " + currentVersion);
            }

            Instant now = Instant.now();
            long version = expectedVersion;
            for (Object event : events) {
                PanacheStoredEventEntity entity = new PanacheStoredEventEntity();
                entity.aggregateId = aggregateId;
                entity.version = version;
                entity.position = nextPosition();
                entity.eventType = event.getClass().getName();
                entity.payload = JsonKit.toJson(event);
                entity.timestamp = now;
                entityManager.persist(entity);
                version++;
            }

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
     * 事件载荷通过 {@link EventDeserializer} 反序列化，类型无法还原时回退为 {@code Map}。
     */
    @Override
    public List<StoredEvent> read(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return entityManager.createQuery(
                        "SELECT e FROM PanacheStoredEventEntity e WHERE e.aggregateId = :aggregateId ORDER BY e.version ASC",
                        PanacheStoredEventEntity.class)
                .setParameter("aggregateId", aggregateId)
                .getResultList()
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
        return entityManager.createQuery(
                        "SELECT e FROM PanacheStoredEventEntity e WHERE e.position >= :fromPosition ORDER BY e.position ASC",
                        PanacheStoredEventEntity.class)
                .setParameter("fromPosition", fromPosition)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    /**
     * 读取聚合流当前版本（事件数量，即最大版本 + 1）。
     *
     * @param aggregateId 聚合 ID
     * @return 流当前版本；空流为 {@code 0L}
     */
    private long findCurrentVersion(String aggregateId) {
        try {
            Long count = entityManager.createQuery(
                            "SELECT COUNT(e) FROM PanacheStoredEventEntity e WHERE e.aggregateId = :aggregateId",
                            Long.class)
                    .setParameter("aggregateId", aggregateId)
                    .getSingleResult();
            return count != null ? count : 0L;
        } catch (NoResultException e) {
            return 0L;
        }
    }

    /**
     * 分配下一个全局 position（行锁读取当前最大 position + 1）。
     *
     * <p>以 {@code ORDER BY position DESC + setMaxResults(1) + PESSIMISTIC_WRITE}
     * （SQL 层 {@code LIMIT 1 FOR UPDATE}）锁定当前最大行：并发 append 在此互斥，
     * 串行化分配，消除热路径上的 {@code uk_position} 冲突。同事务内逐条调用时，
     * auto-flush 先刷入本批刚插入的行，查询即可见新最大值，批内 position 严格递增。
     * 空表首写无行可锁，仍由唯一约束兜底。
     *
     * @return 下一个可用的全局 position
     */
    private long nextPosition() {
        List<Long> max = entityManager.createQuery(
                        "SELECT e.position FROM PanacheStoredEventEntity e ORDER BY e.position DESC",
                        Long.class)
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return (max.isEmpty() ? 0L : max.get(0)) + 1L;
    }

    /**
     * 将持久化实体转换为 core {@link StoredEvent}。
     *
     * <p>事件类型通过 {@link EventDeserializer} 还原，payload 通过 {@link JsonKit} 反序列化。
     * 若事件类不存在（被删除/重命名），回退为 {@code Map}。
     *
     * @param entity 持久化实体
     * @return 重建的存储事件
     */
    private StoredEvent toStoredEvent(PanacheStoredEventEntity entity) {
        Object event = EventDeserializer.deserialize(entity.payload, entity.eventType);
        return new StoredEvent(
                entity.aggregateId,
                entity.version,
                event,
                entity.position,
                entity.timestamp);
    }
}
