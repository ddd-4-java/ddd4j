package io.ddd4j.dropwizard;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dropwizard 应用通过 bootstrap.addBundle(new Ddd4jBundle&lt;&gt;()) 接入 ddd4j。
 */
public final class Ddd4jBundle<C extends Configuration> implements ConfiguredBundle<C> {

    private final Collection<CommandExecutor<?>> executors;
    private final Collection<Consumer<Object>> listeners;
    private final SubjectProvider subjectProvider;
    private final I18nProvider i18nProvider;

    public Ddd4jBundle() {
        this(List.of(), List.of(), null, I18nProvider.DEFAULT);
    }

    public Ddd4jBundle(Collection<CommandExecutor<?>> executors, Collection<Consumer<Object>> listeners,
                      SubjectProvider subjectProvider, I18nProvider i18nProvider) {
        this.executors = List.copyOf(Objects.requireNonNull(executors, "executors must not be null"));
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners must not be null"));
        this.subjectProvider = subjectProvider;
        this.i18nProvider = Objects.requireNonNull(i18nProvider, "i18nProvider must not be null");
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap must not be null");
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
        environment.lifecycle().manage(new Ddd4jDropwizardRuntime(
                publisher, effectiveSubject, i18nProvider, commandBus));
    }
}
