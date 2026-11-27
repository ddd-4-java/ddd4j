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

/**
 * Dropwizard Managed 生命周期中的 ddd4j SPI 注册器。
 */
public final class Ddd4jDropwizardRuntime implements Managed, AutoCloseable {

    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u7ba1\u7406\u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668\u53ca\u5176\u6062\u590d\u52a8\u4f5c\u3002 */
    private final SubjectKitRegistrationScope subjectRegistration;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * \u4f7f\u7528\u9ed8\u8ba4\u5c31\u7eea\u68c0\u67e5\u96c6\u5408\u521b\u5efa\u8fd0\u884c\u65f6\u3002
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u5668
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     */
    public Ddd4jDropwizardRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                  I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5c31\u7eea\u68c0\u67e5\u96c6\u5408\u521b\u5efa\u8fd0\u884c\u65f6\u3002
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u5668
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     * @param readinessContributors \u5c31\u7eea\u68c0\u67e5\u8d21\u732e\u8005
     */
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

    /** \u542f\u52a8 DDD4J \u8fd0\u884c\u65f6\u5e76\u63a5\u5165\u5df2\u88c5\u914d\u7684\u6295\u5f71\u7ba1\u7406\u5668\u3002 */
    @Override
    public void start() {
        registrations.start();
        subjectRegistration.start();
    }

    /**
     * \u83b7\u53d6\u8fd0\u884c\u65f6\u5c31\u7eea\u6ce8\u518c\u8868\u3002
     * @return \u5c31\u7eea\u6ce8\u518c\u8868
     */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /** \u505c\u6b62\u6295\u5f71\u4efb\u52a1\u5e76\u5173\u95ed\u5f53\u524d\u8fd0\u884c\u65f6\u3002 */
    @Override
    public void stop() {
        close();
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Dropwizard 实现调用 <code>subjectRegistration.close()</code>。</p>
     */
    @Override
    public void close() {
        subjectRegistration.close();
        registrations.close();
    }
}
