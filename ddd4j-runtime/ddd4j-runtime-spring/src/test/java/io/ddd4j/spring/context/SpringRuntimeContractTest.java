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

class SpringRuntimeContractTest extends AbstractRuntimeContractTest {

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
