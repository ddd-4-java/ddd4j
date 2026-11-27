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

    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry = new RuntimeReadinessRegistry();
    /** \u7531 CDI \u6269\u5c55\u83b7\u53d6\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u4e0a\u4e0b\u6587\u5f15\u7528\u3002 */
    private final List<CreationalContext<?>> references = new ArrayList<>();
    /** \u5f53\u524d\u5bb9\u5668\u88c5\u914d\u5e76\u8d1f\u8d23\u5173\u95ed\u7684 DDD4J \u8fd0\u884c\u65f6\u3002 */
    private Ddd4jHelidonRuntime runtime;
    /** \u8d1f\u8d23\u5728\u5e94\u7528\u751f\u547d\u5468\u671f\u5185\u7ba1\u7406\u6295\u5f71\u4efb\u52a1\u7684\u7ec4\u4ef6\u3002 */
    private HelidonProjectionViewManager projectionManager;
    /** \u5f53\u524d\u8fd0\u884c\u5b9e\u4f8b\u62e5\u6709\u5e76\u8d1f\u8d23\u5173\u95ed\u7684\u8c03\u5ea6\u5668\u3002 */
    private HelidonProjectionScheduler ownedScheduler;

    /**
     * \u6267\u884c\u6e05\u7406\u52a8\u4f5c\u5e76\u5c06\u540e\u7eed\u5931\u8d25\u5408\u5e76\u81f3\u5df2\u6709\u5f02\u5e38\u3002
     * @param firstFailure \u5df2\u8bb0\u5f55\u7684\u539f\u59cb\u6e05\u7406\u5f02\u5e38
     * @param action \u5f53\u524d\u9700\u8981\u5c1d\u8bd5\u6267\u884c\u7684\u6e05\u7406\u56de\u8c03
     * @return \u539f\u5f02\u5e38\u6216\u5f53\u524d\u65b0\u51fa\u73b0\u7684\u6e05\u7406\u5f02\u5e38
     */
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

    /**
     * \u6839\u636e\u914d\u7f6e\u4e3a CDI \u5bb9\u5668\u767b\u8bb0 DDD4J \u6295\u5f71\u7ec4\u4ef6\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     */
    void registerProjection(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
        if (projectionEnabled()) {
            event.addBean().addType(ViewManager.class).addType(HelidonProjectionViewManager.class)
                    .scope(Singleton.class).createWith(context -> createProjection(beanManager));
        }
    }

    /**
     * \u67e5\u770b\u5f53\u524d Helidon \u914d\u7f6e\u662f\u5426\u542f\u7528\u6295\u5f71\u3002
     * @return \u6295\u5f71\u5df2\u914d\u7f6e\u5f00\u542f\u65f6\u8fd4\u56de true
     */
    private boolean projectionEnabled() {
        return new Ddd4jHelidonConfig().value("ddd4j.cqrs.projection.enabled", Boolean.class).orElse(false);
    }

    /**
     * \u4ece CDI \u89e3\u6790\u5fc5\u8981\u7ec4\u4ef6\u5e76\u88c5\u914d Helidon \u89c6\u56fe\u7ba1\u7406\u5668\u3002
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @return \u7528\u4e8e\u8fd0\u884c\u663e\u5f0f\u6295\u5f71\u914d\u7f6e\u7684\u7ba1\u7406\u5668
     */
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

    /**
     * \u4e3a CDI \u751f\u547d\u5468\u671f\u6ce8\u518c DDD4J \u5c31\u7eea\u68c0\u67e5\u6ce8\u518c\u8868\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void registerReadinessRegistry(@Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(RuntimeReadinessRegistry.class)
                .scope(Singleton.class)
                .createWith(context -> readinessRegistry);
    }

    /**
     * \u5728 CDI \u90e8\u7f72\u5b8c\u6210\u540e\u521b\u5efa\u5e76\u542f\u52a8 DDD4J \u8fd0\u884c\u65f6\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     */
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

    /**
     * \u5728\u5bb9\u5668\u5173\u95ed\u524d\u505c\u6b62\u8fd0\u884c\u65f6\u3001\u6295\u5f71\u5e76\u6e05\u7406\u5df2\u83b7\u53d6\u5f15\u7528\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
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

    /**
     * \u4ece CDI \u5bb9\u5668\u67e5\u627e\u5e76\u6536\u96c6\u547d\u4ee4\u6267\u884c\u5668\u3002
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @return \u5bb9\u5668\u4e2d\u53ef\u89e3\u6790\u7684\u547d\u4ee4\u6267\u884c\u5668\u96c6\u5408
     */
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

    /**
     * \u4ece CDI \u627e\u5230\u53ef\u53c2\u4e0e\u5e94\u7528\u5c31\u7eea\u68c0\u67e5\u7684\u8d21\u732e\u8005\u3002
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @return \u5e94\u7528\u7684\u5c31\u7eea\u68c0\u67e5\u8d21\u732e\u8005\u96c6\u5408
     */
    private List<ReadinessContributor> readinessContributors(BeanManager beanManager) {
        List<ReadinessContributor> contributors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(ReadinessContributor.class)) {
            contributors.add(reference(beanManager, bean, ReadinessContributor.class));
        }
        return contributors;
    }

    /**
     * \u4ece CDI \u4e2d\u89e3\u6790\u6307\u5b9a\u7c7b\u578b\u7684\u5e94\u7528\u7ec4\u4ef6\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @param type \u8981\u89e3\u6790\u7684 Bean \u7c7b\u578b\u6216 CDI \u7c7b\u578b\u4fe1\u606f
     * @return \u89e3\u6790\u5230\u7684\u7ec4\u4ef6\u5b9e\u4f8b
     */
    private <T> T bean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        if (beans.isEmpty()) {
            return null;
        }
        Bean<?> bean = beanManager.resolve(beans);
        return reference(beanManager, bean, type);
    }

    /**
     * \u5408\u5e76\u65e7\u88f8\u7c7b\u578b\u4e0e\u5177\u4f53\u4e1a\u52a1\u6cdb\u578b\u7684 CDI \u53d1\u73b0\u7ed3\u679c\uff0c\u540c\u4e00\u4e2a Bean \u53ea\u6ce8\u518c\u4e00\u6b21\u3002
     *
     * @param beanManager \u7528\u4e8e\u67e5\u8be2 CDI Bean \u7684\u7ba1\u7406\u5668
     * @param rawType \u6cdb\u578b\u67e5\u8be2\u5931\u8d25\u65f6\u7528\u6765\u7ee7\u7eed\u5339\u914d\u7684\u539f\u59cb\u7c7b\u578b
     * @param genericType \u7528\u4e8e\u5339\u914d Bean \u7684\u6cdb\u578b\u7c7b\u578b
     * @return \u4e0e\u6307\u5b9a\u7c7b\u578b\u5339\u914d\u7684 CDI Bean \u96c6\u5408
     */
    private Set<Bean<?>> projectionBeans(BeanManager beanManager, Class<?> rawType, Type genericType) {
        Set<Bean<?>> beans = new LinkedHashSet<>(beanManager.getBeans(rawType));
        if (!rawType.equals(genericType)) {
            beans.addAll(beanManager.getBeans(genericType));
        }
        return beans;
    }

    /**
     * \u4ec5\u5728\u88c5\u914d\u671f\u68c0\u67e5 CDI \u5df2\u7ba1\u7406\u7684\u771f\u5b9e\u76ee\u6807\uff1b\u8fd0\u884c\u65f6\u4ecd\u8c03\u7528\u4ee3\u7406\u4ee5\u4fdd\u7559\u62e6\u622a\u5668\u8bed\u4e49\u3002
     *
     * @param beanManager \u7528\u4e8e\u67e5\u8be2 CDI Bean \u7684\u7ba1\u7406\u5668
     * @param lookupType \u7528\u4e8e\u67e5\u627e CDI Bean \u7684\u7c7b\u578b
     * @param type \u8fd4\u56de\u7ec4\u4ef6\u5e94\u5b9e\u73b0\u7684\u7c7b\u578b
     * @param rejectedImplementation \u5e94\u4ece\u67e5\u627e\u7ed3\u679c\u4e2d\u6392\u9664\u7684\u5b9e\u73b0
     * @param <T> \u6295\u5f71\u7ec4\u4ef6\u7684\u671f\u671b\u7c7b\u578b
     * @return \u627e\u5230\u7684\u6295\u5f71\u7ec4\u4ef6\u5f15\u7528
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

    /**
     * \u83b7\u53d6 CDI Bean \u5f15\u7528\uff0c\u5e76\u767b\u8bb0\u9000\u51fa\u65f6\u9700\u91ca\u653e\u7684 CreationalContext\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @param bean \u88ab\u9009\u5b9a\u7528\u4e8e\u521b\u5efa\u5f15\u7528\u7684 CDI Bean \u5bf9\u8c61
     * @param type \u8981\u89e3\u6790\u7684 Bean \u7c7b\u578b\u6216 CDI \u7c7b\u578b\u4fe1\u606f
     * @return \u5f53\u524d\u90e8\u7f72\u53ef\u4f7f\u7528\u7684 CDI Bean \u5f15\u7528
     */
    private <T> T reference(BeanManager beanManager, Bean<?> bean, Class<T> type) {
        return reference(beanManager, bean, type, type);
    }

    /**
     * \u83b7\u53d6 CDI Bean \u5f15\u7528\uff0c\u5e76\u767b\u8bb0\u9000\u51fa\u65f6\u9700\u91ca\u653e\u7684 CreationalContext\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @param bean \u88ab\u9009\u5b9a\u7528\u4e8e\u521b\u5efa\u5f15\u7528\u7684 CDI Bean \u5bf9\u8c61
     * @param lookupType \u4ece CDI \u67e5\u627e\u6216\u83b7\u53d6\u5f15\u7528\u65f6\u6307\u5b9a\u7684\u7c7b\u578b
     * @param type \u8981\u89e3\u6790\u7684 Bean \u7c7b\u578b\u6216 CDI \u7c7b\u578b\u4fe1\u606f
     * @return \u5f53\u524d\u90e8\u7f72\u53ef\u4f7f\u7528\u7684 CDI Bean \u5f15\u7528
     */
    private <T> T reference(BeanManager beanManager, Bean<?> bean, Type lookupType, Class<T> type) {
        CreationalContext<?> context = beanManager.createCreationalContext(bean);
        references.add(context);
        Type referenceType = bean.getTypes().contains(type) ? type : lookupType;
        return type.cast(beanManager.getReference(bean, referenceType, context));
    }
}
