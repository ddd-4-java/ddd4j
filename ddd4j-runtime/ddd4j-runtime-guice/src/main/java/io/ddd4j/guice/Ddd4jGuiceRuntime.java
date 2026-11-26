package io.ddd4j.guice;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Injector;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.guice.cqrs.GuiceViewManager;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Guice 容器与 ddd4j 全局 SPI 的生命周期桥梁。
 */
public final class Ddd4jGuiceRuntime implements AutoCloseable {

    private final Injector injector;
    private final SpiRegistrationScope registrations;
    private final RuntimeReadinessRegistry readinessRegistry;

    @Inject
    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(injector, publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus,
                             Collection<? extends ReadinessContributor> readinessContributors) {
        this.injector = Objects.requireNonNull(injector, "injector must not be null");
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
        GuiceContext.setInjector(this.injector);
    }

    public void start() {
        registrations.start();
    }

    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    @Override
    public void close() {
        try {
            Binding<GuiceViewManager> binding = injector.getExistingBinding(Key.get(GuiceViewManager.class));
            if (Objects.nonNull(binding)) {
                binding.getProvider().get().close();
            }
        } finally {
            registrations.close();
            GuiceContext.clear();
        }
    }
}
