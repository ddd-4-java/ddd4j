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
package io.ddd4j.guice;

import com.google.common.eventbus.EventBus;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.guice.cqrs.GuiceViewManager;
import io.ddd4j.guice.event.GuiceDomainEventPublisher;
import io.ddd4j.guice.i18n.GuiceI18nProvider;
import io.ddd4j.guice.subject.GuiceSubjectProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ddd4j Google Guice 核心模块。
 * <p>
 * 注册核心 SPI 的 Guice 实现：
 * <ul>
 *   <li>{@link DomainEventPublisher} → {@link GuiceDomainEventPublisher}（基于 Guava EventBus）</li>
 *   <li>{@link SubjectProvider} → {@link GuiceSubjectProvider}（基于 Guice Injector）</li>
 *   <li>{@link I18nProvider} → {@link GuiceI18nProvider}（基于 ResourceBundle）</li>
 *   <li>{@link ProjectionPositionRepository} → 内存版（{@code InMemoryProjectionPositionRepository}，重启丢失）</li>
 * </ul>
 * <p>
 * 投影位置仓储默认使用内存实现。如需重启后保留投影进度，可使用 JDBC 持久化实现：
 * <pre>{@code
 * // 默认内存模式
 * Injector injector = Guice.createInjector(new Ddd4jGuiceModule());
 *
 * // JDBC 持久化模式（覆盖默认内存绑定）
 * Injector injector = Guice.createInjector(
 *     Modules.override(new Ddd4jGuiceModule())
 *            .with(new Ddd4jJdbcProjectionGuiceModule(dataSource))
 * );
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see Ddd4jJdbcProjectionGuiceModule
 * @since 2.0.x
 */
@Slf4j
public class Ddd4jGuiceModule extends AbstractModule {

    @Override
    protected void configure() {
        // 启动期注册并托管全局 SPI；应用关闭时可从 Injector 获取后调用 close()。
        bind(Ddd4jGuiceRuntime.class).asEagerSingleton();
        // 注册 3 个核心 SPI
        bind(DomainEventPublisher.class).to(GuiceDomainEventPublisher.class).in(Singleton.class);
        bind(SubjectProvider.class).to(GuiceSubjectProvider.class).in(Singleton.class);
        bind(I18nProvider.class).to(GuiceI18nProvider.class).in(Singleton.class);
        bind(ProjectionPositionRepository.class).to(InMemoryProjectionPositionRepository.class).in(Singleton.class);
        // 默认线程池大小绑定（可通过 GuicePropertyLoader 覆盖）
        bindConstant().annotatedWith(Names.named("ddd4j.view-manager.thread-pool-size")).to(2);
        bind(GuiceViewManager.class).in(Singleton.class);
        bind(ViewManager.class).to(GuiceViewManager.class);
        bind(ViewScheduler.class).to(GuiceViewManager.class);
    }

    /**
     * 提供 Guava EventBus（单例）
     */
    @Provides
    @Singleton
    public EventBus eventBus() {
        EventBus bus = new EventBus();
        log.info("Guava EventBus created");
        return bus;
    }

    /**
     * 默认投影运行器。业务侧可在自己的模块中绑定更具体的事件读取器和运行器。
     */
    @Provides
    @Singleton
    public ProjectionRunner<Object> projectionRunner(ProjectionService projectionService) {
        return new ProjectionRunner<>(projectionService, new NoopEventChunkReader<>());
    }

    @Provides
    @Singleton
    public ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * 从当前 Injector 中收集命令执行器，构建与其他运行时一致的命令总线。
     */
    @Provides
    @Singleton
    public CommandBus commandBus(Injector injector) {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        for (Binding<?> binding : injector.getAllBindings().values()) {
            Class<?> rawType = binding.getKey().getTypeLiteral().getRawType();
            if (!CommandExecutor.class.isAssignableFrom(rawType) || CommandExecutor.class.equals(rawType)) {
                continue;
            }
            CommandExecutor<?> executor = commandExecutor(injector, binding.getKey());
            if (Objects.nonNull(executor)) {
                executors.add(executor);
            }
        }
        return new DefaultCommandBus(executors);
    }

    @SuppressWarnings("unchecked")
    private CommandExecutor<?> commandExecutor(Injector injector, Key<?> key) {
        Object instance = injector.getInstance((Key<Object>) key);
        return instance instanceof CommandExecutor<?> executor ? executor : null;
    }
}
