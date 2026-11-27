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
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;

import java.util.Collection;
import java.util.Objects;

/**
 * Micronaut 装配适配器；视图执行和生命周期状态由共享管理器拥有。
 */
public class MicronautProjectionViewManager implements ViewManager {
    /**
     * 新 runtime 的显式持久投影开关；不改变旧 GAV 的缺省激活规则。
     */
    public static final String ENABLED = "ddd4j.cqrs.projection.enabled";
    /** \u5b9e\u73b0 Micronaut \u6295\u5f71\u4efb\u52a1\u88c5\u914d\u540e\u7684\u542f\u52a8\u4e0e\u505c\u6b62\u3002 */
    private final DefaultViewManager<Object> delegate;

    /**
     * 收集原生视图，保留构造时快照。
     *
     * @param scheduler 调度器
     * @param runner    运行器
     * @param context   容器
     */
    @SuppressWarnings("unchecked")
    public MicronautProjectionViewManager(ViewScheduler scheduler, ProjectionRunner<?> runner, BeanContext context) {
        validateExplicitConfiguration(context);
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner,
                (Collection<ProjectionView<Object>>) (Collection<?>) context.getBeansOfType(ProjectionView.class));
    }

    /**
     * 保留旧代理构造路径；不产生调度任务。
     */
    protected MicronautProjectionViewManager() {
        delegate = null;
    }

    /**
     * 判断新 runtime 的持久投影开关。
     *
     * @param context 容器
     * @return 是否显式启用
     */
    public static boolean explicitlyEnabled(BeanContext context) {
        return context instanceof ApplicationContext application
                && application.getEnvironment().getProperty(ENABLED, Boolean.class).orElse(false);
    }

    /**
     * 在任何旧、新生命周期启动前验证持久依赖；不创建默认来源或位置仓储。
     *
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

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>Micronaut 实现调用 <code>delegate.start()</code>。</p>
     */
    @Override
    public void start() {
        if (Objects.nonNull(delegate)) {
            delegate.start();
        }
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>Micronaut 实现调用 <code>delegate.stop()</code>。</p>
     */
    @Override
    public void stop() {
        if (Objects.nonNull(delegate)) {
            delegate.stop();
        }
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>Micronaut 实现调用 <code>delegate.isRunning()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(delegate) &amp;&amp; delegate.isRunning()</code>
     */
    @Override
    public boolean isRunning() {
        return Objects.nonNull(delegate) && delegate.isRunning();
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>Micronaut 实现调用 <code>delegate.triggerOnce()</code>。</p>
     */
    @Override
    public void triggerOnce() {
        if (Objects.nonNull(delegate)) {
            delegate.triggerOnce();
        }
    }

    /**
     * 汇总当前登记的投影视图及其运行状态。
     *
     * <p>Micronaut 实现调用 <code>delegate.getProjectionStatus(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 投影状态快照
     */
    @Override
    public ProjectionStatus getProjectionStatus(String streamId) {
        return Objects.nonNull(delegate) ? delegate.getProjectionStatus(streamId) : ProjectionStatus.baseline(streamId, false);
    }
}
