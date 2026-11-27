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
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
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
     * \u628a Micronaut \u4e8b\u4ef6\u53d1\u5e03\u5668\u9002\u914d\u4e3a\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u7aef\u53e3\u3002
     * @param publisher \u7528\u6765\u5bf9\u5916\u5206\u53d1\u6846\u67b6\u4e8b\u4ef6\u7684\u53d1\u5e03\u5668
     * @return \u652f\u6301\u53d1\u5e03 DDD4J \u9886\u57df\u4e8b\u4ef6\u7684\u6846\u67b6\u9002\u914d\u5668
     */
    @Singleton
    @Requires(missingBeans = DomainEventPublisher.class)
    DomainEventPublisher domainEventPublisher(ApplicationEventPublisher<Object> publisher) {
        return new MicronautDomainEventPublisher(publisher);
    }

    /**
     * \u4e3a\u6ca1\u6709\u663e\u5f0f\u8ba4\u8bc1\u5b9e\u73b0\u7684\u5e94\u7528\u88c5\u914d\u9ed8\u8ba4\u4e3b\u4f53\u63d0\u4f9b\u5668\u3002
     * @param publisher \u7528\u6765\u5bf9\u5916\u5206\u53d1\u6846\u67b6\u4e8b\u4ef6\u7684\u53d1\u5e03\u5668
     * @return \u4e0e\u5e94\u7528\u9886\u57df\u4e8b\u4ef6\u5173\u8054\u7684\u9ed8\u8ba4\u4e3b\u4f53\u63d0\u4f9b\u5668
     */
    @Singleton
    @Requires(missingBeans = SubjectProvider.class)
    SubjectProvider subjectProvider(DomainEventPublisher publisher) {
        return new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
    }

    /**
     * \u63d0\u4f9b Micronaut \u5e94\u7528\u7684\u9ed8\u8ba4\u672c\u5730\u5316\u6d88\u606f\u7ec4\u4ef6\u3002
     * @return \u5e94\u7528\u4f7f\u7528\u7684\u56fd\u9645\u5316\u63d0\u4f9b\u5668
     */
    @Singleton
    @Requires(missingBeans = I18nProvider.class)
    I18nProvider i18nProvider() {
        return I18nProvider.DEFAULT;
    }

    /**
     * 按 <code>command Bus</code> 的契约计算并返回结果。
     *
     * @param executors 传入此操作的 <code>Collection&lt;CommandExecutor&lt;?&gt;&gt;</code> 类型 <code>executors</code>
     * @return 当前操作得到的 <code>CommandBus</code> 结果
     */
    @Singleton
    @Requires(missingBeans = CommandBus.class)
    CommandBus commandBus(Collection<CommandExecutor<?>> executors) {
        return new DefaultCommandBus(executors);
    }

    /**
     * 读取<code>runtime</code> 的当前值。
     *
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u6d88\u606f\u63d0\u4f9b\u5668
     * @param commandBus \u5f53\u524d\u547d\u4ee4\u603b\u7ebf
     * @param readinessContributors \u5e94\u7528\u5c31\u7eea\u68c0\u67e5\u8d21\u732e\u8005
     * @return 返回表达式 <code>runtime</code>
     */
    @Singleton
    @Context
    Ddd4jMicronautRuntime runtime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                  I18nProvider i18nProvider, CommandBus commandBus,
                                  Collection<ReadinessContributor> readinessContributors) {
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(publisher, subjectProvider,
                i18nProvider, commandBus, readinessContributors);
        runtime.start();
        return runtime;
    }

    /**
     * \u66b4\u9732\u5f53\u524d\u8fd0\u884c\u65f6\u4f7f\u7528\u7684\u5c31\u7eea\u68c0\u67e5\u6ce8\u518c\u8868\u3002
     * @param runtime \u5f53\u524d\u6846\u67b6\u5df2\u88c5\u914d\u7684 DDD4J \u8fd0\u884c\u65f6
     * @return \u5e94\u7528\u5171\u4eab\u7684\u5c31\u7eea\u68c0\u67e5\u6ce8\u518c\u8868
     */
    @Singleton
    RuntimeReadinessRegistry runtimeReadinessRegistry(Ddd4jMicronautRuntime runtime) {
        return runtime.readiness();
    }
}
