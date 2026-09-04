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
package io.ddd4j.runtime.testkit;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;

/**
 * 各运行时适配器用于共享 CQRS 契约测试的最小控制面。
 * <p>
 * 抽象运行时需提供的最小 CQRS 能力，让 CI 用同一套用例跑多个运行时，
 * 保证跨运行时行为一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface CqrsRuntimeContract extends AutoCloseable {

    /**
     * 返回运行时名称（用于日志与测试报告）。
     *
     * @return 运行时名称
     */
    String runtimeName();

    /**
     * 返回命令总线实例。
     *
     * @return 命令总线
     */
    CommandBus commandBus();

    /**
     * 返回视图管理器实例。
     *
     * @return 视图管理器
     */
    ViewManager viewManager();

    /**
     * 返回视图调度器实例。
     *
     * @return 视图调度器
     */
    ViewScheduler viewScheduler();

    /**
     * 返回投影位置仓储实例。
     *
     * @return 投影位置仓储
     */
    ProjectionPositionRepository positionRepository();

    /**
     * 清理资源。
     */
    @Override
    void close();
}
