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
import jakarta.persistence.FlushModeType;
import jakarta.persistence.NoResultException;

import java.util.List;
import java.util.Objects;

/**
 * 基于 {@link EntityManager} 的 {@link JpaStoredEventRepository} 实现。
 *
 * <p>纯 JPA 实现，不依赖 Spring Data 或任何特定 JPA 提供者。
 * 所有查询均使用 JPQL，保持框架无关性。
 *
 * <p><b>position 生成策略</b></p>
 * <p>使用 {@code SELECT COALESCE(MAX(position), 0) + 1} 生成全局单调 position。
 * 此策略在单实例部署下保证严格递增；在多实例高并发场景下，
 * 建议切换为数据库序列（如 PostgreSQL 的 {@code NEXTVAL}）以避免争用。
 *
 * <p><b>并发冲突契约：</b>{@code MAX(position)} 读取无行锁保护，并发 append 可能
 * 读到相同值导致 {@code uk_position} 唯一约束冲突，事务被回滚。调用方应捕获
 * {@code ConstraintViolationException} 并按指数退避重试整个 append 流程；版本号
 * 若已变化则回到乐观锁失败分支。高并发多实例场景应切换为数据库序列。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class JpaStoredEventRepositoryImpl implements JpaStoredEventRepository {

    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
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

    /**
     * 查询指定聚合事件流的当前版本。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合根标识
     * @return 指定事件流当前的聚合版本
     */
    @Override
    public long findCurrentVersion(String aggregateType, String aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        try {
            Long count = entityManager.createQuery(
                            "SELECT COUNT(e) FROM StoredEventEntity e WHERE e.aggregateType = :aggregateType AND e.aggregateId = :aggregateId",
                            Long.class)
                    .setParameter("aggregateType", aggregateType)
                    .setParameter("aggregateId", aggregateId)
                    .getSingleResult();
            return count != null ? count : 0L;
        } catch (NoResultException e) {
            return 0L;
        }
    }

    /**
     * 按版本升序查询指定聚合的事件记录。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合根标识
     * @return 事件实体列表（版本升序）
     */
    @Override
    public List<StoredEventEntity> findByAggregateTypeAndAggregateIdOrderByVersionAsc(String aggregateType, String aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return entityManager.createQuery(
                        "SELECT e FROM StoredEventEntity e WHERE e.aggregateType = :aggregateType AND e.aggregateId = :aggregateId ORDER BY e.version ASC",
                        StoredEventEntity.class)
                .setParameter("aggregateType", aggregateType)
                .setParameter("aggregateId", aggregateId)
                .getResultList();
    }

    /**
     * 按版本升序查询指定聚合在给定版本范围内的事件记录。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合根标识
     * @param fromVersion   起始版本（包含）
     * @param toVersion     结束版本（包含）
     * @return 事件实体列表（版本升序）
     */
    @Override
    public List<StoredEventEntity> findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(
            String aggregateType, String aggregateId, long fromVersion, long toVersion) {
        return entityManager.createQuery(
                        "SELECT e FROM StoredEventEntity e WHERE e.aggregateType = :aggregateType AND e.aggregateId = :aggregateId AND e.version BETWEEN :fromVersion AND :toVersion ORDER BY e.version ASC",
                        StoredEventEntity.class)
                .setParameter("aggregateType", aggregateType)
                .setParameter("aggregateId", aggregateId)
                .setParameter("fromVersion", fromVersion)
                .setParameter("toVersion", toVersion)
                .getResultList();
    }

    /**
     * 按全局位置升序读取起点及其后的事件记录。
     *
     * @param fromPosition 起始 position（包含）
     * @param limit        最大返回条数
     * @return 事件实体列表（position 升序）
     */
    @Override
    public List<StoredEventEntity> findByPositionGreaterThanEqualOrderByPositionAsc(long fromPosition, int limit) {
        return entityManager.createQuery(
                        "SELECT e FROM StoredEventEntity e WHERE e.position >= :fromPosition ORDER BY e.position ASC",
                        StoredEventEntity.class)
                .setParameter("fromPosition", fromPosition)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * 写入或更新给定的领域对象或存储记录。
     *
     * <p>JPA 实现调用 <code>entityManager.persist(entity)</code>。</p>
     *
     * @param entity 事件实体
     */
    @Override
    public void save(StoredEventEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        entityManager.persist(entity);
    }

    /**
     * 分配事件存储中的下一个全局位置。
     *
     * <p>JPA 实现调用 <code>entityManager.createQuery("SELECT COALESCE(MAX(e.position), 0) FROM StoredEventEntity e", Long.class)</code>。</p>
     *
     * @return 新分配的事件全局位置
     */
    @Override
    public long nextPosition() {
        try {
            Long maxPosition = entityManager.createQuery(
                            "SELECT COALESCE(MAX(e.position), 0) FROM StoredEventEntity e",
                            Long.class)
                    // 位置查询必须看到本事务之前保存的事件，不改变 EntityManager 的 flush 模式。
                    .setFlushMode(FlushModeType.AUTO)
                    .getSingleResult();
            return (maxPosition != null ? maxPosition : 0L) + 1L;
        } catch (NoResultException e) {
            return 1L;
        }
    }
}
