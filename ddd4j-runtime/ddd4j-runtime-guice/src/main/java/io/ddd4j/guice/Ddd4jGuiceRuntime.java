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

    /** \u4e3a\u5f53\u524d Guice \u8fd0\u884c\u65f6\u63d0\u4f9b\u670d\u52a1\u7684\u6ce8\u5165\u5668\u3002 */
    private final Injector injector;
    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * \u4f7f\u7528\u9ed8\u8ba4\u5c31\u7eea\u68c0\u67e5\u96c6\u5408\u521b\u5efa Guice \u8fd0\u884c\u65f6\u3002
     * @param injector Guice \u6ce8\u5165\u5668
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u5668
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     */
    @Inject
    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(injector, publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5c31\u7eea\u68c0\u67e5\u96c6\u5408\u521b\u5efa Guice \u8fd0\u884c\u65f6\u3002
     * @param injector Guice \u6ce8\u5165\u5668
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u5668
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     * @param readinessContributors \u5c31\u7eea\u68c0\u67e5\u8d21\u732e\u8005
     */
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

    /** \u542f\u52a8 ddd4j \u5168\u5c40 SPI \u6ce8\u518c\u3002 */
    public void start() {
        registrations.start();
    }

    /**
     * \u83b7\u53d6\u8fd0\u884c\u65f6\u5c31\u7eea\u6ce8\u518c\u8868\u3002
     * @return \u5c31\u7eea\u6ce8\u518c\u8868
     */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>injector.getExistingBinding(Key.get(GuiceViewManager.class))</code>。</p>
     */
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
