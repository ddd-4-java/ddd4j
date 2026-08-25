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
package io.ddd4j.helidon.cqrs;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helidon 运行时 CQRS 契约测试。
 * <p>
 * 使用 core 组件纯装配方式，与模块现有 {@code Ddd4jHelidonRuntimeTest} 风格一致。
 * Helidon 模块目前无框架级 CQRS 适配器，本测试证明该运行时能承载 core CQRS 组件。
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
 * @since 3.0.0
 */
class HelidonCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {

    @Override
    protected CqrsRuntimeContract createContract() {
        StubExecutorA executorA = new StubExecutorA();
        StubExecutorB executorB = new StubExecutorB();

        DefaultCommandBus commandBus = new DefaultCommandBus(List.of(executorA, executorB));
        StubViewManager viewManager = new StubViewManager();
        InMemoryProjectionPositionRepository positionRepository = new InMemoryProjectionPositionRepository();

        return new CqrsRuntimeContract() {

            @Override
            public String runtimeName() {
                return "helidon";
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

        private final AtomicBoolean running = new AtomicBoolean(false);

        @Override
        public void start() {
            running.set(true);
        }

        @Override
        public void stop() {
            running.set(false);
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

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
