package io.ddd4j.helidon;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Helidon MP 启动和关闭阶段的 ddd4j SPI CDI Extension。
 */
@Slf4j
public final class Ddd4jHelidonExtension implements Extension {

    private Ddd4jHelidonRuntime runtime;

    void start(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);
        SubjectProvider subjectProvider = bean(beanManager, SubjectProvider.class);
        if (Objects.isNull(subjectProvider)) {
            subjectProvider = new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        }
        I18nProvider i18nProvider = bean(beanManager, I18nProvider.class);
        if (Objects.isNull(i18nProvider)) {
            i18nProvider = I18nProvider.DEFAULT;
        }
        CommandBus commandBus = bean(beanManager, CommandBus.class);
        if (Objects.isNull(commandBus)) {
            commandBus = new DefaultCommandBus(commandExecutors(beanManager));
        }
        runtime = new Ddd4jHelidonRuntime(publisher, subjectProvider, i18nProvider, commandBus);
        runtime.start();
        log.info("ddd4j Helidon runtime initialized");
    }

    void stop(@Observes BeforeShutdown event) {
        if (Objects.nonNull(runtime)) {
            runtime.close();
        }
    }

    private List<CommandExecutor<?>> commandExecutors(BeanManager beanManager) {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(CommandExecutor.class)) {
            CommandExecutor<?> executor = reference(beanManager, bean, CommandExecutor.class);
            executors.add(executor);
        }
        return executors;
    }

    private <T> T bean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        if (beans.isEmpty()) {
            return null;
        }
        Bean<?> bean = beanManager.resolve(beans);
        return reference(beanManager, bean, type);
    }

    private <T> T reference(BeanManager beanManager, Bean<?> bean, Class<T> type) {
        CreationalContext<?> context = beanManager.createCreationalContext(bean);
        return type.cast(beanManager.getReference(bean, type, context));
    }
}
