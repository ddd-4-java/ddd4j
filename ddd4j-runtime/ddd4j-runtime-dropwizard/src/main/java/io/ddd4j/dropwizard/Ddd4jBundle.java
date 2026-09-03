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
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.Collections;

/**
 * Dropwizard 应用通过 bootstrap.addBundle(new Ddd4jBundle&lt;&gt;()) 接入 ddd4j。
 */
public final class Ddd4jBundle<C extends Configuration> implements ConfiguredBundle<C> {

    private final Collection<CommandExecutor<?>> executors;
    private final Collection<Consumer<Object>> listeners;
    private final SubjectProvider subjectProvider;
    private final I18nProvider i18nProvider;
    private final Collection<ReadinessContributor> readinessContributors;

    public Ddd4jBundle() {
        this(Collections.emptyList(), Collections.emptyList(), null, I18nProvider.DEFAULT, Collections.emptyList());
    }

    public Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                      SubjectProvider subjectProvider, I18nProvider i18nProvider) {
        this(executors, listeners, subjectProvider, i18nProvider, Collections.emptyList());
    }

    public Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                      SubjectProvider subjectProvider, I18nProvider i18nProvider,
                      Collection<? extends ReadinessContributor> readinessContributors) {
        this.executors = Collections.unmodifiableList(new java.util.ArrayList<>(Objects.requireNonNull(executors, "executors must not be null")));
        this.listeners = Collections.unmodifiableList(new java.util.ArrayList<>(Objects.requireNonNull(listeners, "listeners must not be null")));
        this.subjectProvider = subjectProvider;
        this.i18nProvider = Objects.requireNonNull(i18nProvider, "i18nProvider must not be null");
        this.readinessContributors = Collections.unmodifiableList(new java.util.ArrayList<>(Objects.requireNonNull(readinessContributors,
                "readinessContributors must not be null")));
    }

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
