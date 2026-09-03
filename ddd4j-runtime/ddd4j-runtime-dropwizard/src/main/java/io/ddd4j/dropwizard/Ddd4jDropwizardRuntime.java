package io.ddd4j.dropwizard;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.dropwizard.lifecycle.Managed;

import java.util.Collection;
import java.util.List;
import java.util.Collections;

/**
 * Dropwizard Managed 生命周期中的 ddd4j SPI 注册器。
 */
public final class Ddd4jDropwizardRuntime implements Managed, AutoCloseable {

    private final SpiRegistrationScope registrations;
    private final SubjectKitRegistrationScope subjectRegistration;
    private final RuntimeReadinessRegistry readinessRegistry;

    public Ddd4jDropwizardRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                 I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, Collections.emptyList());
    }

    public Ddd4jDropwizardRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                  I18nProvider i18nProvider, CommandBus commandBus,
                                  Collection<? extends ReadinessContributor> readinessContributors) {
        this.registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher)
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider)
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider)
                .register(SpiKeys.COMMAND_BUS, CommandBus.class, commandBus);
        this.subjectRegistration = new SubjectKitRegistrationScope(subjectProvider);
        this.readinessRegistry = new RuntimeReadinessRegistry(readinessContributors);
    }

    @Override
    public void start() {
        registrations.start();
        subjectRegistration.start();
    }

    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void close() {
        subjectRegistration.close();
        registrations.close();
    }
}
