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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Quarkus 标准 JPA 投影位置仓储（{@link ProjectionPositionRepository} SPI 实现）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
@Typed(QuarkusJpaProjectionPositionRepository.class)
public class QuarkusJpaProjectionPositionRepository implements ProjectionPositionRepository {

    /**
     * 用于从 CDI 获取当前 JPA EntityManager 的查询入口。
     */
    @Inject
    Instance<EntityManager> entityManagers;

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>Optional.ofNullable(manager().find(QuarkusJpaProjectionPosition.class, streamId))</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(manager().find(QuarkusJpaProjectionPosition.class, streamId))
                .map(position -> (ProjectionPosition) position);
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>JPA 实现调用 <code>manager().createQuery("select p from QuarkusJpaProjectionPosition p", QuarkusJpaProjectionPosition.class)</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<ProjectionPosition> findAll() {
        return manager().createQuery("select p from QuarkusJpaProjectionPosition p",
                        QuarkusJpaProjectionPosition.class).getResultList().stream()
                .map(position -> (ProjectionPosition) position)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>position.getStreamId()</code>。</p>
     *
     * @param position 投影位置
     * @return 根据执行路径返回<code>entity</code>、<code>entityManager.merge(entity)</code>
     */
    @Override
    @Transactional
    public ProjectionPosition save(ProjectionPosition position) {
        QuarkusJpaProjectionPosition entity = position instanceof QuarkusJpaProjectionPosition
                ? (QuarkusJpaProjectionPosition) position
                : new QuarkusJpaProjectionPosition(position.getStreamId(), position.getNextEventNumber());
        EntityManager entityManager = manager();
        if (Objects.isNull(entityManager.find(QuarkusJpaProjectionPosition.class, entity.getStreamId()))) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>manager()</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        EntityManager entityManager = manager();
        Optional.ofNullable(entityManager.find(QuarkusJpaProjectionPosition.class, streamId))
                .ifPresent(entityManager::remove);
    }

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>JPA 实现调用 <code>manager()</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        manager().createQuery("update QuarkusJpaProjectionPosition p set p.nextEventNumber = 0 "
                        + "where p.streamId = :streamId")
                .setParameter("streamId", streamId).executeUpdate();
    }

    /**
     * 解析当前调用对应的 JPA 实体管理器。
     *
     * @return 用于投影位点读写的实体管理器
     */
    private EntityManager manager() {
        if (entityManagers.isUnsatisfied() || entityManagers.isAmbiguous()) {
            throw new IllegalStateException("No unique EntityManager is available. Add a Quarkus ORM extension "
                    + "before using the JPA projection repository.");
        }
        return entityManagers.get();
    }
}
