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
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

/**
 * Quarkus CQRS 默认 CDI 生产者。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@ApplicationScoped
public class QuarkusCqrsProducer {

    /**
     * \u521b\u5efa\u9ed8\u8ba4\u7684\u5185\u5b58\u6295\u5f71\u4f4d\u7f6e\u4ed3\u5e93\u3002
     * @return \u6295\u5f71\u4f4d\u7f6e\u4ed3\u5e93
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionPositionRepository projectionPositionRepository() {
        return new InMemoryProjectionPositionRepository();
    }

    /**
     * \u521b\u5efa\u6295\u5f71\u670d\u52a1\u3002
     * @param repository \u6295\u5f71\u4f4d\u7f6e\u4ed3\u5e93
     * @return \u6295\u5f71\u670d\u52a1
     */
    @Produces
    @DefaultBean
    @Singleton
    public ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * \u521b\u5efa\u6295\u5f71\u6267\u884c\u5668\u3002
     * @param projectionService \u6295\u5f71\u670d\u52a1
     * @param chunkReaders \u4e8b\u4ef6\u5757\u8bfb\u53d6\u5668
     * @return \u6295\u5f71\u6267\u884c\u5668
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
