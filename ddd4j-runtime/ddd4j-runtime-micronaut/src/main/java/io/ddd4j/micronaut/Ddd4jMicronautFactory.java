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
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
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

    @Singleton
    @Requires(missingBeans = DomainEventPublisher.class)
    DomainEventPublisher domainEventPublisher(ApplicationEventPublisher<Object> publisher) {
        return new MicronautDomainEventPublisher(publisher);
    }

    @Singleton
    @Requires(missingBeans = SubjectProvider.class)
    SubjectProvider subjectProvider(DomainEventPublisher publisher) {
        return new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
    }

    @Singleton
    @Requires(missingBeans = I18nProvider.class)
    I18nProvider i18nProvider() {
        return I18nProvider.DEFAULT;
    }

    @Singleton
    @Requires(missingBeans = CommandBus.class)
    CommandBus commandBus(Collection<CommandExecutor<?>> executors) {
        return new DefaultCommandBus(executors);
    }

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

    @Singleton
    RuntimeReadinessRegistry runtimeReadinessRegistry(Ddd4jMicronautRuntime runtime) {
        return runtime.readiness();
    }
}
