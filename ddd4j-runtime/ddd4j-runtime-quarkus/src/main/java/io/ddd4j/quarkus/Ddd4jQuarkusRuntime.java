package io.ddd4j.quarkus;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Quarkus CDI 生命周期使用的 ddd4j 全局 SPI 注册作用域。
 */
public final class Ddd4jQuarkusRuntime implements AutoCloseable {

    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * \u4f7f\u7528\u9ed8\u8ba4\u5c31\u7eea\u68c0\u67e5\u9879\u521b\u5efa\u8fd0\u884c\u65f6\u3002
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u8005
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u8005
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     */
    public Ddd4jQuarkusRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5c31\u7eea\u68c0\u67e5\u9879\u521b\u5efa\u8fd0\u884c\u65f6\u3002
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u8005
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u8005
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     * @param readinessContributors \u5c31\u7eea\u68c0\u67e5\u9879
     */
    public Ddd4jQuarkusRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus,
                               Collection<? extends ReadinessContributor> readinessContributors) {
        this(publisher, subjectProvider, i18nProvider, commandBus,
                new RuntimeReadinessRegistry(readinessContributors));
    }

    /**
     * \u4f7f\u7528\u5c31\u7eea\u6ce8\u518c\u8868\u521b\u5efa\u8fd0\u884c\u65f6\u5e76\u6ce8\u518c\u6846\u67b6 SPI\u3002
     * @param publisher \u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u4e3b\u4f53\u63d0\u4f9b\u8005
     * @param i18nProvider \u56fd\u9645\u5316\u63d0\u4f9b\u8005
     * @param commandBus \u547d\u4ee4\u603b\u7ebf
     * @param readinessRegistry \u5c31\u7eea\u72b6\u6001\u6ce8\u518c\u8868
     */
    public Ddd4jQuarkusRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus,
                               RuntimeReadinessRegistry readinessRegistry) {
        registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                        Objects.requireNonNull(publisher, "publisher must not be null"))
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                        Objects.requireNonNull(subjectProvider, "subjectProvider must not be null"))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                        Objects.requireNonNull(i18nProvider, "i18nProvider must not be null"))
                .register(SpiKeys.COMMAND_BUS, CommandBus.class,
                        Objects.requireNonNull(commandBus, "commandBus must not be null"));
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    /** \u542f\u52a8\u6846\u67b6 SPI \u6ce8\u518c\u4f5c\u7528\u57df\u3002 */
    public void start() {
        registrations.start();
    }

    /** @return \u8fd0\u884c\u65f6\u5c31\u7eea\u72b6\u6001\u6ce8\u518c\u8868 */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Quarkus 实现调用 <code>registrations.close()</code>。</p>
     */
    @Override
    public void close() {
        registrations.close();
    }
}
