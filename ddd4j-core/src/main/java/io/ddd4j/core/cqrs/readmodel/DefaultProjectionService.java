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
package io.ddd4j.core.cqrs.readmodel;

import java.util.Objects;

/**
 * 基于 {@link ProjectionPositionRepository} 的默认投影位置服务。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class DefaultProjectionService implements ProjectionService {

    /** \u8bfb\u53d6\u5e76\u4fdd\u5b58\u6295\u5f71\u6d88\u8d39\u4f4d\u7f6e\u7684\u4ed3\u50a8\u3002 */
    private final ProjectionPositionRepository repository;

    /**
     * 使用投影位置仓储创建服务。
     *
     * @param repository 投影位置仓储
     */
    public DefaultProjectionService(ProjectionPositionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public void resetProjectionPosition(String streamId) {
        repository.resetToZero(streamId);
    }

    /** {@inheritDoc} */
    @Override
    public long readProjectionPosition(String streamId) {
        return repository.findByStreamId(streamId)
                .map(ProjectionPosition::getNextEventNumber)
                .orElse(0L);
    }

    /** {@inheritDoc} */
    @Override
    public ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber) {
        ProjectionPosition current = repository.findByStreamId(streamId)
                .orElseGet(() -> DefaultProjectionPosition.zero(streamId));
        return repository.save(current.withNextEventNumber(nextEventNumber));
    }
}
