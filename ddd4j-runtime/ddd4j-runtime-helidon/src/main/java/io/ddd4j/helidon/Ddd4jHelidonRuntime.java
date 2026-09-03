package io.ddd4j.helidon;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Collections;

/**
 * Helidon MP CDI Extension 使用的显式 ddd4j 运行时生命周期。
 */
public final class Ddd4jHelidonRuntime implements AutoCloseable {

    private final SpiRegistrationScope registrations;
    private final SubjectKitRegistrationScope subjectRegistration;
    private final RuntimeReadinessRegistry readinessRegistry;

    public Ddd4jHelidonRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, Collections.emptyList());
    }

    public Ddd4jHelidonRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus,
                               Collection<? extends ReadinessContributor> readinessContributors) {
        this(publisher, subjectProvider, i18nProvider, commandBus,
                new RuntimeReadinessRegistry(readinessContributors));
    }

    public Ddd4jHelidonRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus,
                               RuntimeReadinessRegistry readinessRegistry) {
        this.registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                        Objects.requireNonNull(publisher, "publisher must not be null"))
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                        Objects.requireNonNull(subjectProvider, "subjectProvider must not be null"))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                        Objects.requireNonNull(i18nProvider, "i18nProvider must not be null"))
                .register(SpiKeys.COMMAND_BUS, CommandBus.class,
                        Objects.requireNonNull(commandBus, "commandBus must not be null"));
        this.subjectRegistration = new SubjectKitRegistrationScope(subjectProvider);
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    public void start() {
        registrations.start();
        subjectRegistration.start();
    }

    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    @Override
    public void close() {
        subjectRegistration.close();
        registrations.close();
    }
}
