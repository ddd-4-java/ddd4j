package io.ddd4j.helidon;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;

import java.util.Objects;

/**
 * Helidon MP CDI Extension 使用的显式 ddd4j 运行时生命周期。
 */
public final class Ddd4jHelidonRuntime implements AutoCloseable {

    private final SpiRegistrationScope registrations;
    private final SubjectKitRegistrationScope subjectRegistration;

    public Ddd4jHelidonRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus) {
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
    }

    public void start() {
        registrations.start();
        subjectRegistration.start();
    }

    @Override
    public void close() {
        subjectRegistration.close();
        registrations.close();
    }
}
