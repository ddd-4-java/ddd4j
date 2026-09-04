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
package io.ddd4j.guice.cqrs;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.guice.command.GuiceCommandBus;
import io.ddd4j.runtime.testkit.AbstractCqrsRuntimeContractTest;
import io.ddd4j.runtime.testkit.CqrsRuntimeContract;

import java.util.List;

/**
 * Guice 运行时 CQRS 契约测试。
 * <p>
 * 使用 {@code Guice.createInjector} 装配，与模块现有 {@code GuiceCommandBusTest} 风格一致。
 * <p>
 * 装配方式：
 * <ul>
 *   <li>CommandBus：{@link GuiceCommandBus}，构造器直接注入执行器集合</li>
 *   <li>ViewManager：{@link GuiceViewManager}，无参构造（默认线程池大小 2）</li>
 *   <li>ViewScheduler：{@link GuiceViewManager}（Guice 实现同时实现 ViewManager 和 ViewScheduler）</li>
 *   <li>PositionRepository：{@link GuiceInMemoryProjectionPositionRepository}（Guice 原生内存实现）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
class GuiceCqrsRuntimeContractTest extends AbstractCqrsRuntimeContractTest {

    @Override
    protected CqrsRuntimeContract createContract() {
        StubExecutorA executorA = new StubExecutorA();
        StubExecutorB executorB = new StubExecutorB();

        GuiceCommandBus commandBus = new GuiceCommandBus(List.of(executorA, executorB));
        GuiceViewManager viewManager = new GuiceViewManager();
        GuiceInMemoryProjectionPositionRepository positionRepository =
                new GuiceInMemoryProjectionPositionRepository();

        return new CqrsRuntimeContract() {

            @Override
            public String runtimeName() {
                return "guice";
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
                // GuiceViewManager 同时实现 ViewManager 和 ViewScheduler
                return viewManager;
            }

            @Override
            public ProjectionPositionRepository positionRepository() {
                return positionRepository;
            }

            @Override
            public void close() {
                viewManager.close();
            }
        };
    }
}
