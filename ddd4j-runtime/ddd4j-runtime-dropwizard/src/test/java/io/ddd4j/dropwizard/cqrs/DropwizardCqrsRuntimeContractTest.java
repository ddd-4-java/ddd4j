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
package io.ddd4j.dropwizard.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dropwizard 运行时 CQRS 契约测试。
 * <p>
 * 使用 core 组件纯装配方式，与模块现有 {@code Ddd4jDropwizardRuntimeTest} 风格一致。
 * Dropwizard 模块目前无框架级 CQRS 适配器，本测试证明该运行时能承载 core CQRS 组件。
 * <p>
 * 装配方式：
 * <ul>
 *   <li>CommandBus：{@link DefaultCommandBus}，直接注入测试执行器列表</li>
 *   <li>ViewManager：测试内桩实现，跟踪 start/stop 生命周期</li>
 *   <li>ViewScheduler：测试内空实现</li>
 *   <li>PositionRepository：{@link InMemoryProjectionPositionRepository}（内存版，避免重量级装配）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class DropwizardCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {

    /** {@inheritDoc} */
    @Override
    protected CqrsRuntimeContract createContract() {
        StubExecutorA executorA = new StubExecutorA();
        StubExecutorB executorB = new StubExecutorB();

        DefaultCommandBus commandBus = new DefaultCommandBus(Arrays.asList(executorA, executorB));
        StubViewManager viewManager = new StubViewManager();
        InMemoryProjectionPositionRepository positionRepository = new InMemoryProjectionPositionRepository();

        return new CqrsRuntimeContract() {

            @Override
            public String runtimeName() {
                return "dropwizard";
            }

            @Override
            public CommandBus commandBus() {
                return commandBus;
            }

            @Override
            public ViewManager viewManager() {
                return viewManager;
            }

            @Override
            public ViewScheduler viewScheduler() {
                return new NoopViewScheduler();
            }

            @Override
            public ProjectionPositionRepository positionRepository() {
                return positionRepository;
            }

            @Override
            public void close() {
                viewManager.stop();
            }
        };
    }

    /**
     * 测试内 ViewManager 桩实现，跟踪 start/stop 生命周期。
     */
    private static class StubViewManager implements ViewManager {

        /** 视图管理器是否已启动。 */
        private final AtomicBoolean running = new AtomicBoolean(false);

        /** 执行 <code>start</code>，调用 <code>running.set</code>。 */
        @Override
        public void start() {
            running.set(true);
        }

        /** 执行 <code>stop</code>，调用 <code>running.set</code>。 */
        @Override
        public void stop() {
            running.set(false);
        }

        /**
         * 执行 <code>is Running</code>，调用 <code>running.get</code>。
         *
         * @return 返回表达式 <code>running.get()</code>
         */
        @Override
        public boolean isRunning() {
            return running.get();
        }

        /** 执行 <code>trigger Once</code> 对应的处理。 */
        @Override
        public void triggerOnce() {
            // 桩实现，不做任何操作
        }
    }

    /**
     * 测试内空 ViewScheduler 实现。
     * 契约测试仅验证 ViewManager 生命周期（start/stop/isRunning），不依赖调度。
     */
    private static class NoopViewScheduler implements ViewScheduler {

        /**
         * 执行 <code>schedule</code> 对应的处理。
         *
         * @param viewName 注册调度任务所用的视图名称
         * @param cron 控制任务触发时刻的 Cron 表达式
         * @param task 计划到期时调用的投影任务
         * @return 当前操作得到的 <code>ViewScheduleHandle</code> 结果
         */
        @Override
        public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
            return new ViewScheduleHandle() {
                @Override
                public void cancel() {
                    // noop
                }

                @Override
                public boolean isActive() {
                    return false;
                }
            };
        }
    }
}
