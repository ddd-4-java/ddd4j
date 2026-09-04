package io.ddd4j.micronaut;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Micronaut 容器与 ddd4j 全局 SPI 的生命周期桥梁。
 */
public final class Ddd4jMicronautRuntime implements AutoCloseable {

    private final SpiRegistrationScope registrations;
    private final SubjectKitRegistrationScope subjectRegistration;
    private final RuntimeReadinessRegistry readinessRegistry;

    public Ddd4jMicronautRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                 I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    public Ddd4jMicronautRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                 I18nProvider i18nProvider, CommandBus commandBus,
                                 Collection<? extends ReadinessContributor> readinessContributors) {
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
        this.readinessRegistry = new RuntimeReadinessRegistry(readinessContributors);
    }

    @PostConstruct
    public void start() {
        registrations.start();
        subjectRegistration.start();
    }

    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    @Override
    @PreDestroy
    public void close() {
        subjectRegistration.close();
        registrations.close();
    }
}
