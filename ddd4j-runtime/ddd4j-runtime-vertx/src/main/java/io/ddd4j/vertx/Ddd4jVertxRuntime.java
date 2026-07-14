package io.ddd4j.vertx;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Vert.x 显式运行时。调用方拥有 Vertx 实例，本类不会代为关闭它。
 */
public final class Ddd4jVertxRuntime implements AutoCloseable {

    private final Vertx vertx;
    private final SpiRegistrationScope registrations;

    public Ddd4jVertxRuntime(Vertx vertx, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                            I18nProvider i18nProvider, CommandBus commandBus) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
        this.registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher)
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider)
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider)
                .register(SpiKeys.COMMAND_BUS, CommandBus.class, commandBus);
    }

    public static Ddd4jVertxRuntime create(Vertx vertx, Collection<CommandExecutor<?>> executors) {
        VertxDomainEventPublisher publisher = new VertxDomainEventPublisher(vertx);
        SubjectProvider subjectProvider = new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        return new Ddd4jVertxRuntime(vertx, publisher, subjectProvider, I18nProvider.DEFAULT,
                new DefaultCommandBus(executors));
    }

    public void start() {
        registrations.start();
    }

    public <T> Future<T> executeBlocking(Callable<T> task) {
        Callable<T> actual = Objects.requireNonNull(task, "task must not be null");
        Map<Object, Object> captured = ThreadContext.getResources();
        return vertx.executeBlocking(() -> {
            try (ThreadContext.Scope ignored = ThreadContext.open(captured)) {
                return actual.call();
            }
        });
    }

    @Override
    public void close() {
        registrations.close();
    }
}
