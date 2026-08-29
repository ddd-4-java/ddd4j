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

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import java.util.List;
import java.util.Objects;

/**
 * 基于 {@link EntityManager} 的 {@link JpaStoredEventRepository} 实现。
 *
 * <p>纯 JPA 实现，不依赖 Spring Data 或任何特定 JPA 提供者。
 * 所有查询均使用 JPQL，保持框架无关性。
 *
 * <h3>position 生成策略</h3>
 * <p>行锁读取当前最大 position 后 +1（{@code ORDER BY position DESC + LIMIT 1 +
 * PESSIMISTIC_WRITE}），并发 append 在最大行上互斥串行化，消除热路径上的
 * {@code uk_position} 冲突；空表首写无行可锁仍由唯一约束兜底。
 * 超高吞吐多实例场景建议切换数据库序列。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class JpaStoredEventRepositoryImpl implements JpaStoredEventRepository {

    private final EntityManager entityManager;

    /**
     * 构造仓储实例。
     *
     * @param entityManager JPA 实体管理器（由调用方管理生命周期）
     * @throws NullPointerException entityManager 为 null 时抛出
     */
    public JpaStoredEventRepositoryImpl(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    @Override
    public long findCurrentVersion(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        try {
            Long count = entityManager.createQuery(
                            "SELECT COUNT(e) FROM StoredEventEntity e WHERE e.aggregateId = :aggregateId",
                            Long.class)
                    .setParameter("aggregateId", aggregateId)
                    .getSingleResult();
            return count != null ? count : 0L;
        } catch (NoResultException e) {
            return 0L;
        }
    }

    @Override
    public List<StoredEventEntity> findByAggregateIdOrderByVersionAsc(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return entityManager.createQuery(
                        "SELECT e FROM StoredEventEntity e WHERE e.aggregateId = :aggregateId ORDER BY e.version ASC",
                        StoredEventEntity.class)
                .setParameter("aggregateId", aggregateId)
                .getResultList();
    }

    @Override
    public List<StoredEventEntity> findByPositionGreaterThanEqualOrderByPositionAsc(long fromPosition, int limit) {
        return entityManager.createQuery(
                        "SELECT e FROM StoredEventEntity e WHERE e.position >= :fromPosition ORDER BY e.position ASC",
                        StoredEventEntity.class)
                .setParameter("fromPosition", fromPosition)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public void save(StoredEventEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        entityManager.persist(entity);
    }

    @Override
    public long nextPosition() {
        List<Long> max = entityManager.createQuery(
                        "SELECT e.position FROM StoredEventEntity e ORDER BY e.position DESC",
                        Long.class)
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return (max.isEmpty() ? 0L : max.get(0)) + 1L;
    }
}
