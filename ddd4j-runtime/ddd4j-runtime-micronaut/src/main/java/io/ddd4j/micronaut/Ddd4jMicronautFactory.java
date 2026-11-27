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
package io.ddd4j.micronaut;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.micronaut.command.MicronautCommandBus;
import io.ddd4j.micronaut.cqrs.MicronautProjectionViewManager;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;

import java.util.Collection;

/**
 * Micronaut 默认 Bean 集，业务 Bean 可按类型覆盖默认 Subject 与 I18n 实现。
 */
@Factory
public class Ddd4jMicronautFactory {

    /**
     * 把 Micronaut 事件发布器适配为领域事件发布端口。
     *
     * @param publisher 用来对外分发框架事件的发布器
     * @return 支持发布 DDD4J 领域事件的框架适配器
     */
    @Singleton
    @Requires(missingBeans = DomainEventPublisher.class)
    DomainEventPublisher domainEventPublisher(ApplicationEventPublisher<Object> publisher) {
        return new MicronautDomainEventPublisher(publisher);
    }

    /**
     * 为没有显式认证实现的应用装配默认主体提供器。
     *
     * @param publisher 用来对外分发框架事件的发布器
     * @return 与应用领域事件关联的默认主体提供器
     */
    @Singleton
    @Requires(missingBeans = SubjectProvider.class)
    SubjectProvider subjectProvider(DomainEventPublisher publisher) {
        return new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
    }

    /**
     * 提供 Micronaut 应用的默认本地化消息组件。
     *
     * @return 应用使用的国际化提供器
     */
    @Singleton
    @Requires(missingBeans = I18nProvider.class)
    I18nProvider i18nProvider() {
        return I18nProvider.DEFAULT;
    }

    /**
     * 提供向 Micronaut 命令总线登记执行器的共享注册表。
     *
     * @return 新的命令执行器注册表
     */
    @Singleton
    @Requires(missingBeans = CommandRegistry.class)
    CommandRegistry commandRegistry() {
        return new CommandRegistry();
    }

    /**
     * 从 Micronaut 容器及显式注册项初始化应用命令总线。
     *
     * @param context  当前框架传入的执行或容器上下文
     * @param registry 接收命令执行器的共享注册表
     * @return 装配完毕的 Micronaut 命令总线
     */
    @Singleton
    @Requires(missingBeans = CommandBus.class)
    CommandBus commandBus(BeanContext context, CommandRegistry registry) {
        return new MicronautCommandBus(context, registry);
    }

    /**
     * 解析可选投影管理器，构造并启动当前 Micronaut 运行时。
     *
     * @param publisher             领域事件发布器
     * @param subjectProvider       认证主体提供器
     * @param i18nProvider          国际化消息提供器
     * @param commandBus            当前命令总线
     * @param readinessContributors 应用就绪检查贡献者
     * @param context               解析可选投影组件的 Micronaut 容器
     * @return 已启动的 DDD4J 运行时
     */
    @Singleton
    @Context
    @Bean(preDestroy = "close")
    Ddd4jMicronautRuntime runtime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                  I18nProvider i18nProvider, CommandBus commandBus,
                                  Collection<ReadinessContributor> readinessContributors, BeanContext context) {
        MicronautProjectionViewManager.validateExplicitConfiguration(context);
        ViewManager manager = MicronautProjectionViewManager.explicitlyEnabled(context)
                ? context.getBean(ViewManager.class) : null;
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(publisher, subjectProvider,
                i18nProvider, commandBus, readinessContributors, manager);
        runtime.start();
        return runtime;
    }

    /**
     * 暴露当前运行时使用的就绪检查注册表。
     *
     * @param runtime 当前框架已装配的 DDD4J 运行时
     * @return 应用共享的就绪检查注册表
     */
    @Singleton
    RuntimeReadinessRegistry runtimeReadinessRegistry(Ddd4jMicronautRuntime runtime) {
        return runtime.readiness();
    }
}
