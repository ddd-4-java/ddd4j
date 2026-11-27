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

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.quarkus.command.QuarkusCommandBus;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;
import jakarta.enterprise.inject.Instance;

import java.lang.reflect.Field;

/**
 * Quarkus 运行时 CQRS 契约测试。
 * <p>
 * 使用纯单测方式装配，不依赖 CDI 容器。
 * <p>
 * 装配方式：
 * <ul>
 *   <li>CommandBus：{@link QuarkusCommandBus}，通过反射注入 CDI Instance 绕过 CDI 依赖</li>
 *   <li>ViewManager：{@link QuarkusJpaViewManager}，直接实例化（无 CDI 依赖）</li>
 *   <li>ViewScheduler：{@link NoopViewScheduler}（Quarkus 的 Quartz 调度器在纯单测中不可用）</li>
 *   <li>PositionRepository：{@link InMemoryProjectionPositionRepository}（避免 JPA 重量级装配）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class QuarkusCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {

    /** {@inheritDoc} */
    @Override
    protected CqrsRuntimeContract createContract() {
        QuarkusCommandBus commandBus = new QuarkusCommandBus();
        injectExecutors(commandBus, new StubExecutorA(), new StubExecutorB());

        QuarkusJpaViewManager viewManager = new QuarkusJpaViewManager();
        InMemoryProjectionPositionRepository positionRepository = new InMemoryProjectionPositionRepository();

        return new CqrsRuntimeContract() {

            @Override
            public String runtimeName() {
                return "quarkus";
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
     * \u901a\u8fc7\u53cd\u5c04\u6ce8\u5165 CDI Instance\uff0c\u7ed5\u8fc7 CDI Instance \u4f9d\u8d56\u3002
     *
     * @param commandBus \u5f85\u6ce8\u5165\u6a21\u62df CDI \u6267\u884c\u5668\u7684\u547d\u4ee4\u603b\u7ebf
     * @param executors \u5f85\u6ce8\u5165\u547d\u4ee4\u603b\u7ebf\u7684\u547d\u4ee4\u6267\u884c\u5668
     */
    @SuppressWarnings("unchecked")
    private void injectExecutors(QuarkusCommandBus commandBus, CommandExecutor<?>... executors) {
        try {
            Field field = QuarkusCommandBus.class.getDeclaredField("executors");
            field.setAccessible(true);
            Instance<CommandExecutor<?>> instance = org.mockito.Mockito.mock(Instance.class);
            org.mockito.Mockito.when(instance.iterator()).thenAnswer(ignored -> java.util.Arrays.asList(executors).iterator());
            field.set(commandBus, instance);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Quarkus 的 Quartz 调度器在纯单测中不可用，提供空实现。
     * 契约测试仅验证 ViewManager 生命周期（start/stop/isRunning），不依赖调度。
     */
    private static class NoopViewScheduler implements ViewScheduler {

        /** {@inheritDoc} */
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
