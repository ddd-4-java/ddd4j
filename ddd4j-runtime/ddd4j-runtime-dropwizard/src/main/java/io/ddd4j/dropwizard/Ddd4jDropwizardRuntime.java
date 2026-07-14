package io.ddd4j.dropwizard;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.dropwizard.lifecycle.Managed;

/**
 * Dropwizard Managed 生命周期中的 ddd4j SPI 注册器。
 */
public final class Ddd4jDropwizardRuntime implements Managed, AutoCloseable {

    private final SpiRegistrationScope registrations;

    public Ddd4jDropwizardRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                 I18nProvider i18nProvider, CommandBus commandBus) {
        this.registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher)
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider)
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider)
                .register(SpiKeys.COMMAND_BUS, CommandBus.class, commandBus);
    }

    @Override
    public void start() {
        registrations.start();
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void close() {
        registrations.close();
    }
}
