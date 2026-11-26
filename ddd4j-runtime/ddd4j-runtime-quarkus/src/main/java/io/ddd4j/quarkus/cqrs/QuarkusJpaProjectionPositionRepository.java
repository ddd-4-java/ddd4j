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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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

    @Inject
    Instance<EntityManager> entityManagers;

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(manager().find(QuarkusJpaProjectionPosition.class, streamId))
                .map(position -> (ProjectionPosition) position);
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return manager().createQuery("select p from QuarkusJpaProjectionPosition p",
                        QuarkusJpaProjectionPosition.class).getResultList().stream()
                .map(position -> (ProjectionPosition) position)
                .collect(java.util.stream.Collectors.toList());
    }

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

    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        EntityManager entityManager = manager();
        Optional.ofNullable(entityManager.find(QuarkusJpaProjectionPosition.class, streamId))
                .ifPresent(entityManager::remove);
    }

    @Override
    @Transactional
    public void resetToZero(String streamId) {
        manager().createQuery("update QuarkusJpaProjectionPosition p set p.nextEventNumber = 0 "
                        + "where p.streamId = :streamId")
                .setParameter("streamId", streamId).executeUpdate();
    }

    private EntityManager manager() {
        if (entityManagers.isUnsatisfied() || entityManagers.isAmbiguous()) {
            throw new IllegalStateException("No unique EntityManager is available. Add a Quarkus ORM extension "
                    + "before using the JPA projection repository.");
        }
        return entityManagers.get();
    }
}
