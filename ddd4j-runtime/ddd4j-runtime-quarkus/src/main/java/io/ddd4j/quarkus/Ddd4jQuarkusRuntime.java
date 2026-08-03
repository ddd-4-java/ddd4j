package io.ddd4j.quarkus;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;

import java.util.Objects;

/**
 * Quarkus CDI 生命周期使用的 ddd4j 全局 SPI 注册作用域。
 */
public final class Ddd4jQuarkusRuntime implements AutoCloseable {

    private final SpiRegistrationScope registrations;

    public Ddd4jQuarkusRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus) {
        registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                        Objects.requireNonNull(publisher, "publisher must not be null"))
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                        Objects.requireNonNull(subjectProvider, "subjectProvider must not be null"))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                        Objects.requireNonNull(i18nProvider, "i18nProvider must not be null"))
                .register(SpiKeys.COMMAND_BUS, CommandBus.class,
                        Objects.requireNonNull(commandBus, "commandBus must not be null"));
    }

    public void start() {
        registrations.start();
    }

    @Override
    public void close() {
        registrations.close();
    }
}
