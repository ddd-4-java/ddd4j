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
package io.ddd4j.data.projection.javalin;

import io.ddd4j.core.cqrs.readmodel.DefaultViewManager;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ProjectionStatus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.javalin.Javalin;

import java.util.Collection;
import java.util.Objects;

/** 旧 Javalin 工厂入口，视图生命周期委托共享管理器，线程池由本适配器拥有。 */
public class JavalinProjectionViewManager implements ViewManager, AutoCloseable {
    private final JavalinProjectionScheduler scheduler;
    private final DefaultViewManager<Object> delegate;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JavalinProjectionViewManager(JavalinProjectionScheduler scheduler,
                                         ProjectionRunner<?> runner,
                                         Collection<ProjectionView<?>> views) {
        this.scheduler = scheduler;
        delegate = new DefaultViewManager<>(scheduler, (ProjectionRunner<Object>) runner, (Collection) views);
    }

    /**
     * 创建兼容管理器，不自动启动。应用关闭时应调用 close，stop 仍允许重新启动。
     * @param app Javalin 装配锚点
     * @param views 投影视图
     * @param runner 共享投影运行器
     * @return 管理器
     */
    public static JavalinProjectionViewManager create(Javalin app,
                                                      Collection<ProjectionView<?>> views,
                                                      ProjectionRunner<?> runner) {
        Objects.requireNonNull(app, "app must not be null");
        Objects.requireNonNull(views, "views must not be null");
        Objects.requireNonNull(runner, "runner must not be null");
        JavalinProjectionScheduler scheduler = JavalinProjectionScheduler.create(app, views, runner);
        return new JavalinProjectionViewManager(scheduler, runner, views);
    }

    @Override
    public void start() { delegate.start(); }

    @Override
    public void stop() { delegate.stop(); }

    @Override
    public boolean isRunning() { return delegate.isRunning(); }

    @Override
    public void triggerOnce() { delegate.triggerOnce(); }

    @Override
    public ProjectionStatus getProjectionStatus(String streamId) { return delegate.getProjectionStatus(streamId); }

    @Override
    public void close() {
        try {
            stop();
        } finally {
            scheduler.shutdown();
        }
    }
}
