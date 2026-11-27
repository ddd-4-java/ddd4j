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
     * 用仓储构建持久化投影位点的执行服务。
     *
     * @param repository 保存并读取投影检查点的仓储
     * @return 共享投影服务
     */
    @Singleton
    @Requires(missingBeans = ProjectionService.class)
    ProjectionService service(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * 把投影处理服务和事件读取器装配成运行器。
     *
     * @param service 负责投影事务及同步位点的服务组件
     * @param reader  从事件源按位点分批读取事件的组件
     * @return 负责处理事件批次的投影运行器
     */
    @Singleton
    @Requires(missingBeans = ProjectionRunner.class)
    ProjectionRunner<Object> runner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    /**
     * 创建 Micronaut 默认投影任务调度组件。
     *
     * @return 应用调度投影视图使用的调度器
     */
    @Singleton
    @Bean(preDestroy = "shutdown")
    @Requires(missingBeans = ViewScheduler.class)
    MicronautProjectionScheduler scheduler() {
        return new MicronautProjectionScheduler();
    }

    /**
     * 收集容器视图并构造负责调度的运行时管理器。
     *
     * @param scheduler 调度投影任务的运行时组件
     * @param runner    调用视图并维护事件位点的投影运行器
     * @param context   当前框架传入的执行或容器上下文
     * @return Micronaut 投影视图管理器
     */
    @Singleton
    @Bean(preDestroy = "stop")
    @Requires(missingBeans = ViewManager.class)
    ViewManager manager(ViewScheduler scheduler, ProjectionRunner<?> runner, BeanContext context) {
        return new MicronautProjectionViewManager(scheduler, runner, context);
    }
}
