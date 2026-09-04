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

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版投影位置仓储。
 *
 * <p>适合本地开发、单元测试、Javalin/Guice 默认启动场景。生产环境应由框架适配层
 * 或业务侧替换为数据库、Redis 等持久化实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class InMemoryProjectionPositionRepository implements ProjectionPositionRepository {

    private final ConcurrentMap<String, ProjectionPosition> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(store.get(streamId));
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
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
        save(DefaultProjectionPosition.zero(streamId));
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    public Map<String, ProjectionPosition> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(store));
    }
}
