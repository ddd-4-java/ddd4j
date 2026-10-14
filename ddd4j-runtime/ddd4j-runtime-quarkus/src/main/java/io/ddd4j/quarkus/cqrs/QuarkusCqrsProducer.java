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

import io.ddd4j.core.cqrs.readmodel.*;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Quarkus CQRS 默认 CDI 生产者。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusCqrsProducer {

    /**
     * 创建默认的内存投影位置仓库。
     *
     * @return 投影位置仓库
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionPositionRepository projectionPositionRepository() {
        return new InMemoryProjectionPositionRepository();
    }

    /**
     * 创建投影服务。
     *
     * @param repository 投影位置仓库
     * @return 投影服务
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * 创建投影执行器。
     *
     * @param projectionService 投影服务
     * @param chunkReaders 事件块读取器
     * @return 投影执行器
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionRunner<Object> projectionRunner(
            ProjectionService projectionService,
            Instance<EventChunkReader<Object>> chunkReaders) {
        EventChunkReader<Object> chunkReader = chunkReaders.isResolvable()
                ? chunkReaders.get()
                : new NoopEventChunkReader<>();
        return new ProjectionRunner<>(projectionService, chunkReader);
    }
}
