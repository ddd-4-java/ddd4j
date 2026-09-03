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

import com.google.inject.Inject;
import com.google.inject.Injector;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Collections;

/**
 * Guice 容器与 ddd4j 全局 SPI 的生命周期桥梁。
 */
public final class Ddd4jGuiceRuntime implements AutoCloseable {

    private final SpiRegistrationScope registrations;
    private final RuntimeReadinessRegistry readinessRegistry;

    @Inject
    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(injector, publisher, subjectProvider, i18nProvider, commandBus, Collections.emptyList());
    }

    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus,
                             Collection<? extends ReadinessContributor> readinessContributors) {
        GuiceContext.setInjector(Objects.requireNonNull(injector, "injector must not be null"));
        registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                        Objects.requireNonNull(publisher, "publisher must not be null"))
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                        Objects.requireNonNull(subjectProvider, "subjectProvider must not be null"))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                        Objects.requireNonNull(i18nProvider, "i18nProvider must not be null"))
                .register(SpiKeys.COMMAND_BUS, CommandBus.class,
                        Objects.requireNonNull(commandBus, "commandBus must not be null"));
        readinessRegistry = new RuntimeReadinessRegistry(readinessContributors);
        start();
    }

    public void start() {
        registrations.start();
    }

    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    @Override
    public void close() {
        registrations.close();
        GuiceContext.clear();
    }
}
