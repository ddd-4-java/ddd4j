package io.ddd4j.spring.context;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;
import io.ddd4j.spring.config.SpringCoreConfig;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** \u5b9e\u73b0 <code>AbstractRuntimeContractTest</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>createRuntime()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
class SpringRuntimeContractTest extends AbstractRuntimeContractTest {

    /**
     * 为契约测试构造当前框架的运行实例。
     *
     * <p>测试夹具会调用 <code>context.register(SpringCoreConfig.class)</code>、<code>context.registerBean(SpringContextBridge.class)</code>。</p>
     *
     * @return 待验证的运行时适配器
     */
    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(SpringCoreConfig.class);
        context.registerBean(SpringContextBridge.class);
        context.registerBean(DomainEventPublisher.class, fixtures::publisher);
        context.registerBean(SubjectProvider.class, fixtures::subjectProvider);
        context.registerBean(I18nProvider.class, fixtures::i18nProvider);
        context.registerBean(CommandBus.class, fixtures::commandBus);
        context.registerBean(ReadinessContributor.class, () -> fixtures.readinessContributors().get(0));
        return new RuntimeContractAdapter(() -> {
            if (!context.isActive()) {
                context.refresh();
            }
        }, context::close, fixtures.services(),
                () -> context.getBean(RuntimeReadinessRegistry.class).readiness());
    }
}
