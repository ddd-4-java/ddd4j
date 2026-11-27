/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.helidon;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.helidon.cqrs.HelidonProjectionScheduler;
import io.ddd4j.helidon.cqrs.HelidonProjectionViewManager;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Helidon MP 启动和关闭阶段的 ddd4j SPI CDI Extension。
 */
@Slf4j
public final class Ddd4jHelidonExtension implements Extension {

    private final RuntimeReadinessRegistry readinessRegistry = new RuntimeReadinessRegistry();
    private final List<CreationalContext<?>> references = new ArrayList<>();
    private Ddd4jHelidonRuntime runtime;
    private HelidonProjectionViewManager projectionManager;
    private HelidonProjectionScheduler ownedScheduler;

    private static Throwable cleanup(Throwable firstFailure, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            if (Objects.isNull(firstFailure)) {
                return failure;
            }
            if (firstFailure != failure) {
                firstFailure.addSuppressed(failure);
            }
        }
        return firstFailure;
    }

    void registerProjection(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
        if (projectionEnabled()) {
            event.addBean().addType(ViewManager.class).addType(HelidonProjectionViewManager.class)
                    .scope(Singleton.class).createWith(context -> createProjection(beanManager));
        }
    }

    private boolean projectionEnabled() {
        return new Ddd4jHelidonConfig().value("ddd4j.cqrs.projection.enabled", Boolean.class).orElse(false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HelidonProjectionViewManager createProjection(BeanManager beanManager) {
        EventChunkReader<Object> reader = projectionBean(beanManager, new TypeLiteral<EventChunkReader<?>>() {
                }.getType(),
                EventChunkReader.class, NoopEventChunkReader.class);
        ProjectionPositionRepository positions = projectionBean(beanManager, ProjectionPositionRepository.class,
                ProjectionPositionRepository.class, InMemoryProjectionPositionRepository.class);
        if (Objects.isNull(reader) || Objects.isNull(positions)) {
            throw new IllegalStateException("Explicit Helidon projection requires EventChunkReader and ProjectionPositionRepository");
        }
        List<ProjectionView<?>> views = new ArrayList<>();
        Type viewType = new TypeLiteral<ProjectionView<?>>() {
        }.getType();
        for (Bean<?> viewBean : projectionBeans(beanManager, ProjectionView.class, viewType)) {
            views.add(reference(beanManager, viewBean, viewType, ProjectionView.class));
        }
        ViewScheduler scheduler = bean(beanManager, ViewScheduler.class);
        if (Objects.isNull(scheduler)) {
            ownedScheduler = new HelidonProjectionScheduler();
            scheduler = ownedScheduler;
        }
        projectionManager = new HelidonProjectionViewManager(scheduler,
                new ProjectionRunner<>(new DefaultProjectionService(positions), reader), views);
        return projectionManager;
    }

    void registerReadinessRegistry(@Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(RuntimeReadinessRegistry.class)
                .scope(Singleton.class)
                .createWith(context -> readinessRegistry);
    }

    void start(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        try {
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
            readinessRegistry.registerAll(readinessContributors(beanManager));
            runtime = new Ddd4jHelidonRuntime(publisher, subjectProvider, i18nProvider, commandBus,
                    readinessRegistry);
            runtime.start();
            if (projectionEnabled()) {
                // CDI 解析歧义直接失败，禁止旧、新两套管理器同时启动。
                ViewManager manager = bean(beanManager, ViewManager.class);
                Objects.requireNonNull(manager, "explicit projection manager missing").start();
            }
            log.info("ddd4j Helidon runtime initialized");
        } catch (RuntimeException | Error failure) {
            try {
                stop(null);
            } catch (RuntimeException | Error closeFailure) {
                if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }

    void stop(@Observes BeforeShutdown event) {
        Throwable firstFailure = null;
        if (Objects.nonNull(projectionManager)) {
            firstFailure = cleanup(firstFailure, projectionManager::stop);
        }
        if (Objects.nonNull(ownedScheduler)) {
            firstFailure = cleanup(firstFailure, ownedScheduler::shutdown);
        }
        if (Objects.nonNull(runtime)) {
            firstFailure = cleanup(firstFailure, runtime::close);
        }
        List<CreationalContext<?>> ownedReferences = new ArrayList<>(references);
        references.clear();
        for (CreationalContext<?> reference : ownedReferences) {
            firstFailure = cleanup(firstFailure, reference::release);
        }
        if (firstFailure instanceof RuntimeException) {
            throw (RuntimeException) firstFailure;
        }
        if (firstFailure instanceof Error) {
            throw (Error) firstFailure;
        }
    }

    private List<CommandExecutor<?>> commandExecutors(BeanManager beanManager) {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        Type executorType = new TypeLiteral<CommandExecutor<?>>() {
        }.getType();
        for (Bean<?> bean : projectionBeans(beanManager, CommandExecutor.class, executorType)) {
            CommandExecutor<?> executor = reference(beanManager, bean, executorType, CommandExecutor.class);
            executors.add(executor);
        }
        return executors;
    }

    private List<ReadinessContributor> readinessContributors(BeanManager beanManager) {
        List<ReadinessContributor> contributors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(ReadinessContributor.class)) {
            contributors.add(reference(beanManager, bean, ReadinessContributor.class));
        }
        return contributors;
    }

    private <T> T bean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        if (beans.isEmpty()) {
            return null;
        }
        Bean<?> bean = beanManager.resolve(beans);
        return reference(beanManager, bean, type);
    }

    /**
     * 合并旧裸类型与具体业务泛型的 CDI 发现结果，同一个 Bean 只注册一次。
     */
    private Set<Bean<?>> projectionBeans(BeanManager beanManager, Class<?> rawType, Type genericType) {
        Set<Bean<?>> beans = new LinkedHashSet<>(beanManager.getBeans(rawType));
        if (!rawType.equals(genericType)) {
            beans.addAll(beanManager.getBeans(genericType));
        }
        return beans;
    }

    /**
     * 仅在装配期检查 CDI 已管理的真实目标；运行时仍调用代理以保留拦截器语义。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T projectionBean(BeanManager beanManager, Type lookupType, Class<T> type,
                                 Class<?> rejectedImplementation) {
        Set<Bean<?>> beans = projectionBeans(beanManager, type, lookupType);
        if (beans.isEmpty()) {
            return null;
        }
        Bean<?> bean = beanManager.resolve(beans);
        CreationalContext<?> context = beanManager.createCreationalContext(bean);
        references.add(context);
        Type referenceType = bean.getTypes().contains(type) ? type : lookupType;
        T proxy = type.cast(beanManager.getReference(bean, referenceType, context));
        Object target = proxy;
        if (beanManager.isNormalScope(bean.getScope())) {
            // 同一上下文取目标，不创建第二个 Bean；Dependent 本身即真实引用。
            target = beanManager.getContext(bean.getScope()).get((Contextual) bean, (CreationalContext) context);
        }
        if (rejectedImplementation.isInstance(target)) {
            throw new IllegalStateException("Explicit Helidon projection requires a real event source and durable positions");
        }
        return proxy;
    }

    private <T> T reference(BeanManager beanManager, Bean<?> bean, Class<T> type) {
        return reference(beanManager, bean, type, type);
    }

    private <T> T reference(BeanManager beanManager, Bean<?> bean, Type lookupType, Class<T> type) {
        CreationalContext<?> context = beanManager.createCreationalContext(bean);
        references.add(context);
        Type referenceType = bean.getTypes().contains(type) ? type : lookupType;
        return type.cast(beanManager.getReference(bean, referenceType, context));
    }
}
