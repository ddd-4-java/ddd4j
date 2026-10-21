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
package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring Data JPA 投影位置仓储（{@link ProjectionPositionRepository} SPI 实现）。
 *
 * <p>由 Spring 框架自动注入，业务方无需关心。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Repository
public class SpringJpaProjectionPositionRepository
        implements ProjectionPositionRepository {

    /**
     * Spring Data JPA 仓库代理
     */
    private final JpaRepository<SpringJpaProjectionPosition, String> jpaRepository;

    /**
     * 创建基于 Spring Data JPA 的投影位置仓库。
     *
     * @param jpaRepository Spring Data JPA 仓库
     */
    public SpringJpaProjectionPositionRepository(JpaRepository<SpringJpaProjectionPosition, String> jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return jpaRepository.findById(streamId).map(p -> (ProjectionPosition) p);
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return Collections.unmodifiableList(jpaRepository.findAll().stream()
                .map(p -> (ProjectionPosition) p).collect(Collectors.toList()));
    }

    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        return jpaRepository.save((SpringJpaProjectionPosition) position);
    }

    @Override
    public void deleteByStreamId(String streamId) {
        jpaRepository.deleteById(streamId);
    }

    /**
     * 在事务中重置已有位置；流尚不存在时创建零位置。
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        SpringJpaProjectionPosition position = jpaRepository.findById(streamId)
                .orElseGet(() -> new SpringJpaProjectionPosition(streamId, 0L));
        position.setNextEventNumber(0L);
        jpaRepository.save(position);
    }
}
