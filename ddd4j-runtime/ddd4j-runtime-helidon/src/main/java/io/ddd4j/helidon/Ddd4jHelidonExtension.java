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
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.*;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Helidon MP 启动和关闭阶段的 ddd4j SPI CDI Extension。
 */
@Slf4j
public final class Ddd4jHelidonExtension implements Extension {

    /**
     * 汇总当前组件就绪状态的注册表。
     */
    private final RuntimeReadinessRegistry readinessRegistry = new RuntimeReadinessRegistry();
    /**
     * 由 CDI 扩展获取并负责释放的上下文引用。
     */
    private final List<CreationalContext<?>> references = new ArrayList<>();
    /**
     * 当前容器装配并负责关闭的 DDD4J 运行时。
     */
    private Ddd4jHelidonRuntime runtime;
    /**
     * 负责在应用生命周期内管理投影任务的组件。
     */
    private HelidonProjectionViewManager projectionManager;
    /**
     * 当前运行实例拥有并负责关闭的调度器。
     */
    private HelidonProjectionScheduler ownedScheduler;

    /**
     * 执行清理动作并将后续失败合并至已有异常。
     *
     * @param firstFailure 已记录的原始清理异常
     * @param action       当前需要尝试执行的清理回调
     * @return 原异常或当前新出现的清理异常
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
     * 根据配置为 CDI 容器登记 DDD4J 投影组件。
     *
     * @param event       当前框架生命周期或容器启动事件
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
     */
    void registerProjection(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
        if (projectionEnabled()) {
            event.addBean().addType(ViewManager.class).addType(HelidonProjectionViewManager.class)
                    .scope(Singleton.class).createWith(context -> createProjection(beanManager));
        }
    }

    /**
     * 查看当前 Helidon 配置是否启用投影。
     *
     * @return 投影已配置开启时返回 true
     */
    private boolean projectionEnabled() {
        return new Ddd4jHelidonConfig().value("ddd4j.cqrs.projection.enabled", Boolean.class).orElse(false);
    }

    /**
     * 从 CDI 解析必要组件并装配 Helidon 视图管理器。
     *
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
     * @return 用于运行显式投影配置的管理器
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
     * 为 CDI 生命周期注册 DDD4J 就绪检查注册表。
     *
     * @param event 当前框架生命周期或容器启动事件
     */
    void registerReadinessRegistry(@Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(RuntimeReadinessRegistry.class)
                .scope(Singleton.class)
                .createWith(context -> readinessRegistry);
    }

    /**
     * 在 CDI 部署完成后创建并启动 DDD4J 运行时。
     *
     * @param event       当前框架生命周期或容器启动事件
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
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
     * 在容器关闭前停止运行时、投影并清理已获取引用。
     *
     * @param event 当前框架生命周期或容器启动事件
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
     * 从 CDI 容器查找并收集命令执行器。
     *
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
     * @return 容器中可解析的命令执行器集合
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
     * 从 CDI 找到可参与应用就绪检查的贡献者。
     *
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
     * @return 应用的就绪检查贡献者集合
     */
    private List<ReadinessContributor> readinessContributors(BeanManager beanManager) {
        List<ReadinessContributor> contributors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(ReadinessContributor.class)) {
            contributors.add(reference(beanManager, bean, ReadinessContributor.class));
        }
        return contributors;
    }

    /**
     * 从 CDI 中解析指定类型的应用组件。
     *
     * @param <T>         当前容器处理的组件或事件类型
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
     * @param type        要解析的 Bean 类型或 CDI 类型信息
     * @return 解析到的组件实例
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
     * 合并旧裸类型与具体业务泛型的 CDI 发现结果，同一个 Bean 只注册一次。
     *
     * @param beanManager 用于查询 CDI Bean 的管理器
     * @param rawType     泛型查询失败时用来继续匹配的原始类型
     * @param genericType 用于匹配 Bean 的泛型类型
     * @return 与指定类型匹配的 CDI Bean 集合
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
     *
     * @param beanManager            用于查询 CDI Bean 的管理器
     * @param lookupType             用于查找 CDI Bean 的类型
     * @param type                   返回组件应实现的类型
     * @param rejectedImplementation 应从查找结果中排除的实现
     * @param <T>                    投影组件的期望类型
     * @return 找到的投影组件引用
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
     * 获取 CDI Bean 引用，并登记退出时需释放的 CreationalContext。
     *
     * @param <T>         当前容器处理的组件或事件类型
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
     * @param bean        被选定用于创建引用的 CDI Bean 对象
     * @param type        要解析的 Bean 类型或 CDI 类型信息
     * @return 当前部署可使用的 CDI Bean 引用
     */
    private <T> T reference(BeanManager beanManager, Bean<?> bean, Class<T> type) {
        return reference(beanManager, bean, type, type);
    }

    /**
     * 获取 CDI Bean 引用，并登记退出时需释放的 CreationalContext。
     *
     * @param <T>         当前容器处理的组件或事件类型
     * @param beanManager 用来发现或获取 CDI Bean 的管理器
     * @param bean        被选定用于创建引用的 CDI Bean 对象
     * @param lookupType  从 CDI 查找或获取引用时指定的类型
     * @param type        要解析的 Bean 类型或 CDI 类型信息
     * @return 当前部署可使用的 CDI Bean 引用
     */
    private <T> T reference(BeanManager beanManager, Bean<?> bean, Type lookupType, Class<T> type) {
        CreationalContext<?> context = beanManager.createCreationalContext(bean);
        references.add(context);
        Type referenceType = bean.getTypes().contains(type) ? type : lookupType;
        return type.cast(beanManager.getReference(bean, referenceType, context));
    }
}
