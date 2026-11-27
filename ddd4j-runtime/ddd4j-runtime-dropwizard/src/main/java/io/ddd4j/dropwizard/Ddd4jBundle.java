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
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dropwizard \u5e94\u7528\u901a\u8fc7 bootstrap.addBundle(new Ddd4jBundle&lt;&gt;()) \u63a5\u5165 ddd4j\u3002
 *
 * @param <C> 由 Ddd4jBundle 处理的泛型类型
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

    /** 使用默认服务集合创建 Dropwizard Bundle。 */
    public Ddd4jBundle() {
        this(List.of(), List.of(), null, I18nProvider.DEFAULT, List.of());
    }

    /**
     * 使用指定服务集合创建 Bundle。
     *
     * @param executors 命令执行器
     * @param listeners 事件监听器
     * @param subjectProvider 主体提供器，可为空
     * @param i18nProvider 国际化提供器
     */
    public Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                       SubjectProvider subjectProvider, I18nProvider i18nProvider) {
        this(executors, listeners, subjectProvider, i18nProvider, List.of());
    }

    /**
     * 使用指定服务与就绪检查创建 Bundle。
     *
     * @param executors 命令执行器
     * @param listeners 事件监听器
     * @param subjectProvider 主体提供器，可为空
     * @param i18nProvider 国际化提供器
     * @param readinessContributors 就绪检查贡献者
     */
    public Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                       SubjectProvider subjectProvider, I18nProvider i18nProvider,
                       Collection<? extends ReadinessContributor> readinessContributors) {
        this.executors = List.copyOf(Objects.requireNonNull(executors, "executors must not be null"));
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners must not be null"));
        this.subjectProvider = subjectProvider;
        this.i18nProvider = Objects.requireNonNull(i18nProvider, "i18nProvider must not be null");
        this.readinessContributors = List.copyOf(Objects.requireNonNull(readinessContributors,
                "readinessContributors must not be null"));
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
        DropwizardDomainEventPublisher publisher = new DropwizardDomainEventPublisher(listeners);
        SubjectProvider effectiveSubject = Objects.nonNull(subjectProvider)
                ? subjectProvider
                : new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        CommandBus commandBus = new DefaultCommandBus(executors);
        Ddd4jDropwizardRuntime runtime = new Ddd4jDropwizardRuntime(
                publisher, effectiveSubject, i18nProvider, commandBus, readinessContributors);
        environment.healthChecks().register("ddd4j-readiness",
                new Ddd4jDropwizardReadinessHealthCheck(runtime.readiness()));
        environment.lifecycle().manage(runtime);
    }
}
