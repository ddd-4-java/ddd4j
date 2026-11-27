/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
 * 注册 3 个核心 SPI 的 Guice 实现：
 * <ul>
 *   <li>{@link DomainEventPublisher} → {@link GuiceDomainEventPublisher}（基于 Guava EventBus）</li>
 *   <li>{@link SubjectProvider} → {@link GuiceSubjectProvider}（基于 Guice Injector）</li>
 *   <li>{@link I18nProvider} → {@link GuiceI18nProvider}（基于 ResourceBundle）</li>
 * </ul>
 * <p>
 * 用户在自己的 Guice Injector 中 install(this) 即可启用 ddd4j 全部功能：
 * <pre>{@code
 * Injector injector = Guice.createInjector(new Ddd4jGuiceModule());
 * DomainEventPublisher publisher = injector.getInstance(DomainEventPublisher.class);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class Ddd4jGuiceModule extends AbstractModule {

    /**
     * 创建基础运行时；仅安装核心 SPI 和空闲命令总线，不安装投影、存储或 ORM。
     * 命令执行器仍通过现有 Ddd4jCommandGuiceModule 规则发现。
     *
     * @return 基础 Guice 模块
     */
    public static com.google.inject.Module basic() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Ddd4jGuiceRuntime.class).asEagerSingleton();
                bind(DomainEventPublisher.class).to(GuiceDomainEventPublisher.class).in(Singleton.class);
                bind(SubjectProvider.class).to(GuiceSubjectProvider.class).in(Singleton.class);
                bind(I18nProvider.class).to(GuiceI18nProvider.class).in(Singleton.class);
                bind(EventBus.class).toInstance(new EventBus());
                install(new Ddd4jCommandGuiceModule());
            }
        };
    }

    /** \u628a DDD4J \u8fd0\u884c\u65f6\u4f7f\u7528\u7684\u57fa\u7840\u670d\u52a1\u6ce8\u518c\u5230 Guice\u3002 */
    @Override
    protected void configure() {
        // 启动期注册并托管全局 SPI；应用关闭时可从 Injector 获取后调用 close()。
        bind(Ddd4jGuiceRuntime.class).asEagerSingleton();
        // 注册 3 个核心 SPI
        bind(DomainEventPublisher.class).to(GuiceDomainEventPublisher.class).in(Singleton.class);
        bind(SubjectProvider.class).to(GuiceSubjectProvider.class).in(Singleton.class);
        bind(I18nProvider.class).to(GuiceI18nProvider.class).in(Singleton.class);
        bind(ProjectionPositionRepository.class).to(InMemoryProjectionPositionRepository.class).in(Singleton.class);
        bind(GuiceViewManager.class).in(Singleton.class);
        bind(ViewManager.class).to(GuiceViewManager.class);
        bind(ViewScheduler.class).to(GuiceViewManager.class);
    }

    /**
     * \u63d0\u4f9b Guava EventBus\uff08\u5355\u4f8b\uff09
     *
     * @return Guava EventBus
     */
    @Provides
    @Singleton
    public EventBus eventBus() {
        EventBus bus = new EventBus();
        log.info("Guava EventBus created");
        return bus;
    }

    /**
     * \u9ed8\u8ba4\u6295\u5f71\u8fd0\u884c\u5668\u3002\u4e1a\u52a1\u4fa7\u53ef\u5728\u81ea\u5df1\u7684\u6a21\u5757\u4e2d\u7ed1\u5b9a\u66f4\u5177\u4f53\u7684\u4e8b\u4ef6\u8bfb\u53d6\u5668\u548c\u8fd0\u884c\u5668\u3002
     *
     * @param projectionService \u6295\u5f71\u4f4d\u7f6e\u670d\u52a1
     * @return \u4f7f\u7528\u7a7a\u4e8b\u4ef6\u8bfb\u53d6\u5668\u7684\u6295\u5f71\u8fd0\u884c\u5668
     */
    @Provides
    @Singleton
    public ProjectionRunner<Object> projectionRunner(ProjectionService projectionService) {
        return new ProjectionRunner<>(projectionService, new NoopEventChunkReader<>());
    }

    /**
     * \u4f7f\u7528\u4f4d\u7f6e\u4ed3\u50a8\u63d0\u4f9b\u6295\u5f71\u670d\u52a1\u3002
     * @param repository \u6295\u5f71\u4f4d\u7f6e\u4ed3\u50a8
     * @return \u6295\u5f71\u670d\u52a1
     */
    @Provides
    @Singleton
    public ProjectionService projectionService(ProjectionPositionRepository repository) {
        return new DefaultProjectionService(repository);
    }

    /**
     * \u4ece\u5f53\u524d Injector \u4e2d\u6536\u96c6\u547d\u4ee4\u6267\u884c\u5668\uff0c\u6784\u5efa\u4e0e\u5176\u4ed6\u8fd0\u884c\u65f6\u4e00\u81f4\u7684\u547d\u4ee4\u603b\u7ebf\u3002
     *
     * @param injector 传入此操作的 <code>Injector</code> 类型 <code>injector</code>
     * @return 当前操作得到的 <code>CommandBus</code> 结果
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

    /**
     * 执行 <code>command Executor</code>，调用 <code>injector.getInstance</code>。
     *
     * @param injector 传入此操作的 <code>Injector</code> 类型 <code>injector</code>
     * @param key 传入此操作的 <code>Key&lt;?&gt;</code> 类型 <code>key</code>
     * @return 返回表达式 <code>instance instanceof CommandExecutor&lt;?&gt; executor ? executor : null</code>
     */
    @SuppressWarnings("unchecked")
    private CommandExecutor<?> commandExecutor(Injector injector, Key<?> key) {
        Object instance = injector.getInstance((Key<Object>) key);
        return instance instanceof CommandExecutor<?> executor ? executor : null;
    }
}
