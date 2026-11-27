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
package io.ddd4j.micronaut.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * 显式持久投影接线；不在基础模式装配事件源、存储或后台任务。
 */
@Factory
@Requires(property = MicronautProjectionViewManager.ENABLED, value = "true")
public class MicronautProjectionFactory {
    @Singleton
    @Requires(missingBeans = ProjectionService.class)
    ProjectionService service(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    @Singleton
    @Requires(missingBeans = ProjectionRunner.class)
    ProjectionRunner<Object> runner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    @Singleton
    @Bean(preDestroy = "shutdown")
    @Requires(missingBeans = ViewScheduler.class)
    MicronautProjectionScheduler scheduler() {
        return new MicronautProjectionScheduler();
    }

    @Singleton
    @Bean(preDestroy = "stop")
    @Requires(missingBeans = ViewManager.class)
    ViewManager manager(ViewScheduler scheduler, ProjectionRunner<?> runner, BeanContext context) {
        return new MicronautProjectionViewManager(scheduler, runner, context);
    }
}
