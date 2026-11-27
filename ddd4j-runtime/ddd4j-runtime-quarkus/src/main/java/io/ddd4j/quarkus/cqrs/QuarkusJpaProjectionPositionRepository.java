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
 * @since 1.0.x
 */
@ApplicationScoped
public class QuarkusJpaProjectionPositionRepository implements ProjectionPositionRepository {

    /** \u7528\u4e8e\u4ece CDI \u83b7\u53d6\u5f53\u524d JPA EntityManager \u7684\u67e5\u8be2\u5165\u53e3\u3002 */
    @Inject
    Instance<EntityManager> entityManagers;

    /** {@inheritDoc} */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(manager().find(QuarkusJpaProjectionPosition.class, streamId))
                .map(position -> (ProjectionPosition) position);
    }

    /** {@inheritDoc} */
    @Override
    public List<ProjectionPosition> findAll() {
        return manager().createQuery("select p from QuarkusJpaProjectionPosition p",
                        QuarkusJpaProjectionPosition.class)
                .getResultList().stream()
                .map(position -> (ProjectionPosition) position)
                .collect(java.util.stream.Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProjectionPosition save(ProjectionPosition position) {
        QuarkusJpaProjectionPosition entity = (QuarkusJpaProjectionPosition) position;
        QuarkusJpaProjectionPosition current = manager().find(QuarkusJpaProjectionPosition.class, entity.getStreamId());
        if (Objects.isNull(current)) {
            manager().persist(entity);
            return entity;
        }
        return manager().merge(entity);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        QuarkusJpaProjectionPosition entity = manager().find(QuarkusJpaProjectionPosition.class, streamId);
        if (Objects.nonNull(entity)) {
            manager().remove(entity);
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        manager().createQuery("update QuarkusJpaProjectionPosition p set p.nextEventNumber = 0 "
                        + "where p.streamId = :streamId")
                .setParameter("streamId", streamId)
                .executeUpdate();
    }

    /**
     * \u89e3\u6790\u5f53\u524d\u8c03\u7528\u5bf9\u5e94\u7684 JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u3002
     * @return \u7528\u4e8e\u6295\u5f71\u4f4d\u70b9\u8bfb\u5199\u7684\u5b9e\u4f53\u7ba1\u7406\u5668
     */
    private EntityManager manager() {
        if (entityManagers.isUnsatisfied() || entityManagers.isAmbiguous()) {
            throw new IllegalStateException("No unique EntityManager is available. Add a Quarkus ORM extension "
                    + "before using the JPA projection repository.");
        }
        return entityManagers.get();
    }
}
