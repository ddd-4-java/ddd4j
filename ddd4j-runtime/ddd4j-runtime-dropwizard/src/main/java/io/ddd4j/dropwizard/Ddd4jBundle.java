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
package io.ddd4j.dropwizard;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.dropwizard.command.DropwizardCommandBus;
import io.ddd4j.dropwizard.cqrs.DropwizardProjectionViewManager;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Dropwizard 应用通过 bootstrap.addBundle(new Ddd4jBundle&lt;&gt;()) 接入 ddd4j。
 */
public final class Ddd4jBundle<C extends Configuration> implements ConfiguredBundle<C> {

    /**
     * 框架容器中需要登记到命令总线的执行器。
     */
    private final Collection<CommandExecutor<?>> executors;
    /**
     * 接收运行时发布领域事件的监听回调。
     */
    private final Collection<Consumer<Object>> listeners;
    /**
     * 根据当前调用范围提供认证主体的组件。
     */
    private final SubjectProvider subjectProvider;
    /**
     * 当前运行框架注册的国际化消息提供器。
     */
    private final I18nProvider i18nProvider;
    /**
     * 参与应用就绪状态汇总的运行组件。
     */
    private final Collection<ReadinessContributor> readinessContributors;
    /**
     * 依据 Dropwizard 运行环境创建投影视图管理器的工厂。
     */
    private final Function<Environment, DropwizardProjectionViewManager> projectionFactory;
    /**
     * 当前容器装配并负责关闭的 DDD4J 运行时。
     */
    private Ddd4jDropwizardRuntime runtime;

    /**
     * 使用默认服务集合创建 Dropwizard Bundle。
     */
    public Ddd4jBundle() {
        this(List.of(), List.of(), null, I18nProvider.DEFAULT, List.of());
    }

    /**
     * 使用指定服务集合创建 Bundle。
     *
     * @param executors       命令执行器
     * @param listeners       事件监听器
     * @param subjectProvider 主体提供器，可为空
     * @param i18nProvider    国际化提供器
     */
    public Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                       SubjectProvider subjectProvider, I18nProvider i18nProvider) {
        this(executors, listeners, subjectProvider, i18nProvider, List.of());
    }

    /**
     * 使用指定服务与就绪检查创建 Bundle。
     *
     * @param executors             命令执行器
     * @param listeners             事件监听器
     * @param subjectProvider       主体提供器，可为空
     * @param i18nProvider          国际化提供器
     * @param readinessContributors 就绪检查贡献者
     */
    public Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                       SubjectProvider subjectProvider, I18nProvider i18nProvider,
                       Collection<? extends ReadinessContributor> readinessContributors) {
        this(executors, listeners, subjectProvider, i18nProvider, readinessContributors, null);
    }

    /**
     * 保存 Dropwizard 启动时需装配的认证、事件、命令及投影组件。
     *
     * @param executors             当前框架容器提供的命令执行器
     * @param listeners             接收领域事件的监听回调
     * @param subjectProvider       从当前认证框架解析主体的组件
     * @param i18nProvider          提供本地化消息的框架组件
     * @param readinessContributors 提供运行时就绪检查的组件集合
     * @param projectionFactory     依据运行环境构造视图管理器的工厂
     */
    private Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                        SubjectProvider subjectProvider, I18nProvider i18nProvider,
                        Collection<? extends ReadinessContributor> readinessContributors,
                        Function<Environment, DropwizardProjectionViewManager> projectionFactory) {
        this.projectionFactory = projectionFactory;
        this.executors = List.copyOf(Objects.requireNonNull(executors, "executors must not be null"));
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners must not be null"));
        this.subjectProvider = subjectProvider;
        this.i18nProvider = Objects.requireNonNull(i18nProvider, "i18nProvider must not be null");
        this.readinessContributors = List.copyOf(Objects.requireNonNull(readinessContributors,
                "readinessContributors must not be null"));
    }

    /**
     * 显式启用持久投影，返回保留当前服务快照的新 Bundle。
     *
     * @param <E>        事件类型
     * @param repository 调用方持久位置仓储
     * @param reader     实际事件读取器
     * @param views      视图快照
     * @return 使用自有调度线程的 Bundle
     */
    public <E> Ddd4jBundle<C> withProjection(ProjectionPositionRepository repository,
                                             EventChunkReader<E> reader, Collection<? extends ProjectionView<E>> views) {
        return projected(repository, reader, views, null);
    }

    /**
     * 使用借用调度器启用持久投影；停止只取消本 Bundle 的 handles。
     * 调用方继续负责借用调度器、reader 和仓储生命周期。
     *
     * @param <E>        事件类型
     * @param repository 调用方持久位置仓储
     * @param reader     实际事件读取器
     * @param views      视图快照
     * @param scheduler  调用方调度器
     * @return 显式投影 Bundle
     */
    public <E> Ddd4jBundle<C> withProjection(ProjectionPositionRepository repository,
                                             EventChunkReader<E> reader, Collection<? extends ProjectionView<E>> views, ViewScheduler scheduler) {
        return projected(repository, reader, views, Objects.requireNonNull(scheduler, "scheduler must not be null"));
    }

    /**
     * 构造可以在 Dropwizard 生命周期内运行的显式投影装配。
     *
     * @param <E>        当前容器处理的组件或事件类型
     * @param repository 保存并读取投影检查点的仓储
     * @param reader     从事件源按位点分批读取事件的组件
     * @param views      当前运行时注册的投影视图集合
     * @param scheduler  调度投影任务的运行时组件
     * @return 含投影装配配置的 Dropwizard Bundle
     */
    private <E> Ddd4jBundle<C> projected(ProjectionPositionRepository repository,
                                         EventChunkReader<E> reader, Collection<? extends ProjectionView<E>> views, ViewScheduler scheduler) {
        Objects.requireNonNull(repository, "Explicit projection requires a position repository");
        Objects.requireNonNull(reader, "Explicit projection requires an event reader");
        if (repository instanceof InMemoryProjectionPositionRepository || reader instanceof NoopEventChunkReader<?>) {
            throw new IllegalArgumentException("Explicit durable projection requires a real reader and persistent repository");
        }
        @SuppressWarnings("unchecked")
        Collection<ProjectionView<?>> snapshot = (Collection<ProjectionView<?>>) (Collection<?>) List.copyOf(views);
        return new Ddd4jBundle<>(executors, listeners, subjectProvider, i18nProvider, readinessContributors, env -> {
            ProjectionRunner<E> runner = new ProjectionRunner<>(new DefaultProjectionService(repository), reader);
            return Objects.isNull(scheduler) ? DropwizardProjectionViewManager.create(env, snapshot, runner)
                    : new DropwizardProjectionViewManager(scheduler, runner, snapshot);
        });
    }

    /**
     * @return run 装配后的 Managed 实例，尚未装配时为空
     */
    public Optional<Ddd4jDropwizardRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    /**
     * 执行 Bundle 的引导初始化入口。
     *
     * @param bootstrap Dropwizard 应用引导对象
     */
    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap must not be null");
    }

    /**
     * 把 DDD4J 服务和就绪检查装配到 Dropwizard 应用。
     *
     * @param configuration 当前应用实例加载的配置
     * @param environment   当前 Dropwizard 或 Spring 应用环境
     */
    @Override
    public void run(C configuration, Environment environment) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);
        DropwizardDomainEventPublisher publisher = new DropwizardDomainEventPublisher(listeners);
        SubjectProvider effectiveSubject = Objects.nonNull(subjectProvider)
                ? subjectProvider
                : new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        CommandBus commandBus = new DropwizardCommandBus(executors);
        DropwizardProjectionViewManager manager = Objects.isNull(projectionFactory) ? null : projectionFactory.apply(environment);
        Ddd4jDropwizardRuntime assembled = new Ddd4jDropwizardRuntime(
                publisher, effectiveSubject, i18nProvider, commandBus, readinessContributors, manager);
        environment.healthChecks().register("ddd4j-readiness",
                new Ddd4jDropwizardReadinessHealthCheck(assembled.readiness()));
        environment.lifecycle().manage(assembled);
        runtime = assembled;
    }
}
