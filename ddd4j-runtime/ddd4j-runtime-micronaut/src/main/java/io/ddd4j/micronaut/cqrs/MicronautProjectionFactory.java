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
    /**
     * \u7528\u4ed3\u50a8\u6784\u5efa\u6301\u4e45\u5316\u6295\u5f71\u4f4d\u70b9\u7684\u6267\u884c\u670d\u52a1\u3002
     * @param repository \u4fdd\u5b58\u5e76\u8bfb\u53d6\u6295\u5f71\u68c0\u67e5\u70b9\u7684\u4ed3\u50a8
     * @return \u5171\u4eab\u6295\u5f71\u670d\u52a1
     */
    @Singleton
    @Requires(missingBeans = ProjectionService.class)
    ProjectionService service(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * \u628a\u6295\u5f71\u5904\u7406\u670d\u52a1\u548c\u4e8b\u4ef6\u8bfb\u53d6\u5668\u88c5\u914d\u6210\u8fd0\u884c\u5668\u3002
     * @param service \u8d1f\u8d23\u6295\u5f71\u4e8b\u52a1\u53ca\u540c\u6b65\u4f4d\u70b9\u7684\u670d\u52a1\u7ec4\u4ef6
     * @param reader \u4ece\u4e8b\u4ef6\u6e90\u6309\u4f4d\u70b9\u5206\u6279\u8bfb\u53d6\u4e8b\u4ef6\u7684\u7ec4\u4ef6
     * @return \u8d1f\u8d23\u5904\u7406\u4e8b\u4ef6\u6279\u6b21\u7684\u6295\u5f71\u8fd0\u884c\u5668
     */
    @Singleton
    @Requires(missingBeans = ProjectionRunner.class)
    ProjectionRunner<Object> runner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    /**
     * \u521b\u5efa Micronaut \u9ed8\u8ba4\u6295\u5f71\u4efb\u52a1\u8c03\u5ea6\u7ec4\u4ef6\u3002
     * @return \u5e94\u7528\u8c03\u5ea6\u6295\u5f71\u89c6\u56fe\u4f7f\u7528\u7684\u8c03\u5ea6\u5668
     */
    @Singleton
    @Bean(preDestroy = "shutdown")
    @Requires(missingBeans = ViewScheduler.class)
    MicronautProjectionScheduler scheduler() {
        return new MicronautProjectionScheduler();
    }

    /**
     * \u6536\u96c6\u5bb9\u5668\u89c6\u56fe\u5e76\u6784\u9020\u8d1f\u8d23\u8c03\u5ea6\u7684\u8fd0\u884c\u65f6\u7ba1\u7406\u5668\u3002
     * @param scheduler \u8c03\u5ea6\u6295\u5f71\u4efb\u52a1\u7684\u8fd0\u884c\u65f6\u7ec4\u4ef6
     * @param runner \u8c03\u7528\u89c6\u56fe\u5e76\u7ef4\u62a4\u4e8b\u4ef6\u4f4d\u70b9\u7684\u6295\u5f71\u8fd0\u884c\u5668
     * @param context \u5f53\u524d\u6846\u67b6\u4f20\u5165\u7684\u6267\u884c\u6216\u5bb9\u5668\u4e0a\u4e0b\u6587
     * @return Micronaut \u6295\u5f71\u89c6\u56fe\u7ba1\u7406\u5668
     */
    @Singleton
    @Bean(preDestroy = "stop")
    @Requires(missingBeans = ViewManager.class)
    ViewManager manager(ViewScheduler scheduler, ProjectionRunner<?> runner, BeanContext context) {
        return new MicronautProjectionViewManager(scheduler, runner, context);
    }
}
