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
package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Guice 环境默认的内存版投影位置仓储。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class GuiceInMemoryProjectionPositionRepository implements ProjectionPositionRepository {

    /**
     * 投影位置内存存储
     */
    private final ConcurrentMap<String, ProjectionPosition> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(store.get(streamId));
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        store.put(position.getStreamId(), position);
        return position;
    }

    @Override
    public void deleteByStreamId(String streamId) {
        store.remove(streamId);
    }

    @Override
    public void resetToZero(String streamId) {
        ProjectionPosition old = store.get(streamId);
        if (Objects.nonNull(old)) {
            store.put(streamId, old.withNextEventNumber(0L));
        }
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    public Map<String, ProjectionPosition> snapshot() {
        return Map.copyOf(store);
    }
}
