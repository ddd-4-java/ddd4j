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

import io.ddd4j.core.cqrs.readmodel.DefaultViewManager;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.NoopEventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;

import java.util.Collection;
import java.util.Objects;

/** Micronaut 装配适配器；视图执行和生命周期状态由共享管理器拥有。 */
public class MicronautProjectionViewManager implements ViewManager {
    /** 新 runtime 的显式持久投影开关；不改变旧 GAV 的缺省激活规则。 */
    public static final String ENABLED = "ddd4j.cqrs.projection.enabled";
    private final DefaultViewManager<Object> delegate;

    /** 收集原生视图，保留构造时快照。
     * @param scheduler 调度器
     * @param runner 运行器
     * @param context 容器
     */
    @SuppressWarnings("unchecked")
    public MicronautProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner, BeanContext context) {
        validateExplicitConfiguration(context);
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) context.getBeansOfType(ProjectionView.class));
    }

    /** 保留旧代理构造路径；不产生调度任务。 */
    protected MicronautProjectionViewManager() {
        delegate = null;
    }

    /** 判断新 runtime 的持久投影开关。
     * @param context 容器
     * @return 是否显式启用
     */
    public static boolean explicitlyEnabled(BeanContext context) {
        return context instanceof ApplicationContext application
                && application.getEnvironment().getProperty(ENABLED, Boolean.class).orElse(false);
    }

    /** 在任何旧、新生命周期启动前验证持久依赖；不创建默认来源或位置仓储。
     * @param context 容器
     */
    public static void validateExplicitConfiguration(BeanContext context) {
        Objects.requireNonNull(context, "context must not be null");
        if (!explicitlyEnabled(context)) {
            return;
        }
        Collection<EventChunkReader> readers = context.getBeansOfType(EventChunkReader.class);
        Collection<ProjectionPositionRepository> positions = context.getBeansOfType(ProjectionPositionRepository.class);
        if (readers.size() != 1 || positions.size() != 1) {
            throw new IllegalStateException("Explicit Micronaut projection requires exactly one EventChunkReader and one ProjectionPositionRepository");
        }
        if (readers.iterator().next() instanceof NoopEventChunkReader
                || positions.iterator().next() instanceof InMemoryProjectionPositionRepository) {
            throw new IllegalStateException("Explicit Micronaut projection requires a real event source and durable positions");
        }
    }

    @Override
    public void start() {
        if (Objects.nonNull(delegate)) {
            delegate.start();
        }
    }
    @Override
    public void stop() {
        if (Objects.nonNull(delegate)) {
            delegate.stop();
        }
    }
    @Override
    public boolean isRunning() {
        return Objects.nonNull(delegate) && delegate.isRunning();
    }
    @Override
    public void triggerOnce() {
        if (Objects.nonNull(delegate)) {
            delegate.triggerOnce();
        }
    }
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return Objects.nonNull(delegate) ? delegate.getProjectionStatus(streamId) : ProjectionStatus.baseline(streamId, false);
    }
}
