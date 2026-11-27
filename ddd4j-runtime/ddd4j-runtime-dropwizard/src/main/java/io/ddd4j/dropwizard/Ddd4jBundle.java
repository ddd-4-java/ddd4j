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

    /** \u6846\u67b6\u5bb9\u5668\u4e2d\u9700\u8981\u767b\u8bb0\u5230\u547d\u4ee4\u603b\u7ebf\u7684\u6267\u884c\u5668\u3002 */
    private final Collection<CommandExecutor<?>> executors;
    /** \u63a5\u6536\u8fd0\u884c\u65f6\u53d1\u5e03\u9886\u57df\u4e8b\u4ef6\u7684\u76d1\u542c\u56de\u8c03\u3002 */
    private final Collection<Consumer<Object>> listeners;
    /** \u6839\u636e\u5f53\u524d\u8c03\u7528\u8303\u56f4\u63d0\u4f9b\u8ba4\u8bc1\u4e3b\u4f53\u7684\u7ec4\u4ef6\u3002 */
    private final SubjectProvider subjectProvider;
    /** \u5f53\u524d\u8fd0\u884c\u6846\u67b6\u6ce8\u518c\u7684\u56fd\u9645\u5316\u6d88\u606f\u63d0\u4f9b\u5668\u3002 */
    private final I18nProvider i18nProvider;
    /** \u53c2\u4e0e\u5e94\u7528\u5c31\u7eea\u72b6\u6001\u6c47\u603b\u7684\u8fd0\u884c\u7ec4\u4ef6\u3002 */
    private final Collection<ReadinessContributor> readinessContributors;
    /** \u4f9d\u636e Dropwizard \u8fd0\u884c\u73af\u5883\u521b\u5efa\u6295\u5f71\u89c6\u56fe\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    private final Function<Environment, DropwizardProjectionViewManager> projectionFactory;
    /** \u5f53\u524d\u5bb9\u5668\u88c5\u914d\u5e76\u8d1f\u8d23\u5173\u95ed\u7684 DDD4J \u8fd0\u884c\u65f6\u3002 */
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
     * \u4fdd\u5b58 Dropwizard \u542f\u52a8\u65f6\u9700\u88c5\u914d\u7684\u8ba4\u8bc1\u3001\u4e8b\u4ef6\u3001\u547d\u4ee4\u53ca\u6295\u5f71\u7ec4\u4ef6\u3002
     * @param executors \u5f53\u524d\u6846\u67b6\u5bb9\u5668\u63d0\u4f9b\u7684\u547d\u4ee4\u6267\u884c\u5668
     * @param listeners \u63a5\u6536\u9886\u57df\u4e8b\u4ef6\u7684\u76d1\u542c\u56de\u8c03
     * @param subjectProvider \u4ece\u5f53\u524d\u8ba4\u8bc1\u6846\u67b6\u89e3\u6790\u4e3b\u4f53\u7684\u7ec4\u4ef6
     * @param i18nProvider \u63d0\u4f9b\u672c\u5730\u5316\u6d88\u606f\u7684\u6846\u67b6\u7ec4\u4ef6
     * @param readinessContributors \u63d0\u4f9b\u8fd0\u884c\u65f6\u5c31\u7eea\u68c0\u67e5\u7684\u7ec4\u4ef6\u96c6\u5408
     * @param projectionFactory \u4f9d\u636e\u8fd0\u884c\u73af\u5883\u6784\u9020\u89c6\u56fe\u7ba1\u7406\u5668\u7684\u5de5\u5382
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
     * \u6784\u9020\u53ef\u4ee5\u5728 Dropwizard \u751f\u547d\u5468\u671f\u5185\u8fd0\u884c\u7684\u663e\u5f0f\u6295\u5f71\u88c5\u914d\u3002
     * @param <E> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param repository \u4fdd\u5b58\u5e76\u8bfb\u53d6\u6295\u5f71\u68c0\u67e5\u70b9\u7684\u4ed3\u50a8
     * @param reader \u4ece\u4e8b\u4ef6\u6e90\u6309\u4f4d\u70b9\u5206\u6279\u8bfb\u53d6\u4e8b\u4ef6\u7684\u7ec4\u4ef6
     * @param views \u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u6295\u5f71\u89c6\u56fe\u96c6\u5408
     * @param scheduler \u8c03\u5ea6\u6295\u5f71\u4efb\u52a1\u7684\u8fd0\u884c\u65f6\u7ec4\u4ef6
     * @return \u542b\u6295\u5f71\u88c5\u914d\u914d\u7f6e\u7684 Dropwizard Bundle
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
     * \u6267\u884c Bundle \u7684\u5f15\u5bfc\u521d\u59cb\u5316\u5165\u53e3\u3002
     * @param bootstrap Dropwizard \u5e94\u7528\u5f15\u5bfc\u5bf9\u8c61
     */
    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap must not be null");
    }

    /**
     * \u628a DDD4J \u670d\u52a1\u548c\u5c31\u7eea\u68c0\u67e5\u88c5\u914d\u5230 Dropwizard \u5e94\u7528\u3002
     * @param configuration \u5f53\u524d\u5e94\u7528\u5b9e\u4f8b\u52a0\u8f7d\u7684\u914d\u7f6e
     * @param environment \u5f53\u524d Dropwizard \u6216 Spring \u5e94\u7528\u73af\u5883
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
