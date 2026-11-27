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
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Vert.x 显式运行时。调用方拥有 Vertx 实例，本类不会代为关闭它。
 */
public final class Ddd4jVertxRuntime implements AutoCloseable {

    /** \u5f53\u524d\u6846\u67b6\u7ec4\u4ef6\u5171\u4eab\u7684 Vert.x \u5b9e\u4f8b\u3002 */
    private final Vertx vertx;
    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u7ba1\u7406\u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668\u53ca\u5176\u6062\u590d\u52a8\u4f5c\u3002 */
    private final SubjectKitRegistrationScope subjectRegistration;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * \u4f7f\u7528\u9ed8\u8ba4\u5c31\u7eea\u68c0\u67e5\u96c6\u5408\u521b\u5efa\u663e\u5f0f\u8fd0\u884c\u65f6\u3002
     * @param vertx \u8c03\u7528\u65b9\u6301\u6709\u7684 Vertx \u5b9e\u4f8b
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u5668
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     */
    public Ddd4jVertxRuntime(Vertx vertx, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(vertx, publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * \u521b\u5efa\u5e26\u5c31\u7eea\u68c0\u67e5\u7684\u663e\u5f0f\u8fd0\u884c\u65f6\u3002
     * @param vertx \u8c03\u7528\u65b9\u6301\u6709\u7684 Vertx \u5b9e\u4f8b
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u5668
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     * @param readinessContributors \u5c31\u7eea\u68c0\u67e5\u8d21\u732e\u8005
     */
    public Ddd4jVertxRuntime(Vertx vertx, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus,
                             Collection<? extends ReadinessContributor> readinessContributors) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
        this.registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher)
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider)
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider)
                .register(SpiKeys.COMMAND_BUS, CommandBus.class, commandBus);
        this.subjectRegistration = new SubjectKitRegistrationScope(subjectProvider);
        this.readinessRegistry = new RuntimeReadinessRegistry(readinessContributors);
    }

    /**
     * \u4f7f\u7528\u9ed8\u8ba4\u4e8b\u4ef6\u3001\u4e3b\u4f53\u4e0e\u56fd\u9645\u5316\u5b9e\u73b0\u521b\u5efa\u8fd0\u884c\u65f6\u3002
     * @param vertx \u8c03\u7528\u65b9\u6301\u6709\u7684 Vertx \u5b9e\u4f8b
     * @param executors \u547d\u4ee4\u6267\u884c\u5668
     * @return \u663e\u5f0f\u8fd0\u884c\u65f6
     */
    public static Ddd4jVertxRuntime create(Vertx vertx, Collection<CommandExecutor<?>> executors) {
        VertxDomainEventPublisher publisher = new VertxDomainEventPublisher(vertx);
        SubjectProvider subjectProvider = new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        return new Ddd4jVertxRuntime(vertx, publisher, subjectProvider, I18nProvider.DEFAULT,
                new DefaultCommandBus(executors));
    }

    /**
     * \u83b7\u53d6\u8fd0\u884c\u65f6\u5c31\u7eea\u6ce8\u518c\u8868\u3002
     * @return \u5c31\u7eea\u6ce8\u518c\u8868
     */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /** \u542f\u52a8\u5168\u5c40 SPI \u4e0e\u4e3b\u4f53\u6ce8\u518c\u3002 */
    public void start() {
        registrations.start();
        subjectRegistration.start();
    }

    /**
     * \u5c06\u5f53\u524d\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u4f20\u64ad\u5230 Vert.x \u963b\u585e\u4efb\u52a1\u3002
     * @param <T> \u4efb\u52a1\u7ed3\u679c\u7c7b\u578b
     * @param task \u5f85\u6267\u884c\u4efb\u52a1
     * @return \u5f02\u6b65\u4efb\u52a1\u7ed3\u679c
     */
    public <T> Future<T> executeBlocking(Callable<T> task) {
        Callable<T> actual = Objects.requireNonNull(task, "task must not be null");
        Map<Object, Object> captured = ThreadContext.getResources();
        return vertx.executeBlocking(() -> {
            try (ThreadContext.Scope ignored = ThreadContext.open(captured)) {
                return actual.call();
            }
        });
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Vert.x 实现调用 <code>subjectRegistration.close()</code>。</p>
     */
    @Override
    public void close() {
        subjectRegistration.close();
        registrations.close();
    }
}
