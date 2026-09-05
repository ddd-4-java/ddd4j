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
package io.ddd4j.helidon;

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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Helidon MP 启动和关闭阶段的 ddd4j SPI CDI Extension。
 */
@Slf4j
public final class Ddd4jHelidonExtension implements Extension {

    private final RuntimeReadinessRegistry readinessRegistry = new RuntimeReadinessRegistry();
    private Ddd4jHelidonRuntime runtime;

    void registerReadinessRegistry(@Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(RuntimeReadinessRegistry.class)
                .scope(ApplicationScoped.class)
                .createWith(context -> readinessRegistry);
    }

    void start(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);
        SubjectProvider subjectProvider = bean(beanManager, SubjectProvider.class);
        if (Objects.isNull(subjectProvider)) {
            subjectProvider = new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        }
        I18nProvider i18nProvider = bean(beanManager, I18nProvider.class);
        if (Objects.isNull(i18nProvider)) {
            i18nProvider = I18nProvider.DEFAULT;
        }
        CommandBus commandBus = bean(beanManager, CommandBus.class);
        if (Objects.isNull(commandBus)) {
            commandBus = new DefaultCommandBus(commandExecutors(beanManager));
        }
        readinessRegistry.registerAll(readinessContributors(beanManager));
        runtime = new Ddd4jHelidonRuntime(publisher, subjectProvider, i18nProvider, commandBus,
                readinessRegistry);
        runtime.start();
        log.info("ddd4j Helidon runtime initialized");
    }

    void stop(@Observes BeforeShutdown event) {
        if (Objects.nonNull(runtime)) {
            runtime.close();
        }
    }

    private List<CommandExecutor<?>> commandExecutors(BeanManager beanManager) {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(CommandExecutor.class)) {
            CommandExecutor<?> executor = reference(beanManager, bean, CommandExecutor.class);
            executors.add(executor);
        }
        return executors;
    }

    private List<ReadinessContributor> readinessContributors(BeanManager beanManager) {
        List<ReadinessContributor> contributors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(ReadinessContributor.class)) {
            contributors.add(reference(beanManager, bean, ReadinessContributor.class));
        }
        return contributors;
    }

    private <T> T bean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        if (beans.isEmpty()) {
            return null;
        }
        Bean<?> bean = beanManager.resolve(beans);
        return reference(beanManager, bean, type);
    }

    private <T> T reference(BeanManager beanManager, Bean<?> bean, Class<T> type) {
        CreationalContext<?> context = beanManager.createCreationalContext(bean);
        return type.cast(beanManager.getReference(bean, type, context));
    }
}
