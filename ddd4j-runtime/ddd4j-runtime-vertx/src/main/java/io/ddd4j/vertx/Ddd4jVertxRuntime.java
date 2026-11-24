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
package io.ddd4j.vertx;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.vertx.command.VertxCommandBus;
import io.ddd4j.vertx.cqrs.VertxProjectionViewManager;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.NoopEventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Vert.x 显式运行时。调用方拥有 Vertx 实例，本类不会代为关闭它。
 */
public final class Ddd4jVertxRuntime implements AutoCloseable {

    private VertxProjectionViewManager projectionManager;
    private final Vertx vertx;
    private final SpiRegistrationScope registrations;
    private final SubjectKitRegistrationScope subjectRegistration;
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * 使用默认就绪检查集合创建显式运行时。
     *
     * @param vertx 调用方持有的 Vertx 实例
     * @param publisher 领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider 国际化提供器
     * @param commandBus 命令总线
     */
    public Ddd4jVertxRuntime(Vertx vertx, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(vertx, publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * 创建带就绪检查的显式运行时。
     *
     * @param vertx 调用方持有的 Vertx 实例
     * @param publisher 领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider 国际化提供器
     * @param commandBus 命令总线
     * @param readinessContributors 就绪检查贡献者
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
     * 使用默认事件、主体与国际化实现创建运行时。
     *
     * @param vertx 调用方持有的 Vertx 实例
     * @param executors 命令执行器
     * @return 显式运行时
     */
    public static Ddd4jVertxRuntime create(Vertx vertx, Collection<CommandExecutor<?>> executors) {
        CommandBus commandBus = VertxCommandBus.create(vertx, executors);
        VertxDomainEventPublisher publisher = new VertxDomainEventPublisher(vertx);
        SubjectProvider subjectProvider = new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        return new Ddd4jVertxRuntime(vertx, publisher, subjectProvider, I18nProvider.DEFAULT,
                commandBus);
    }

    /** 显式创建持久投影运行时；缺少实际 reader/store 时在发布 SPI 或注册 timer 前拒绝。
     * 存储和 Vertx 均由调用方拥有；不推导 ORM、事务或业务 schema。
     * @param <E> 事件类型
     * @param vertx 调用方原生实例
     * @param executors 命令执行器
     * @param repository 持久位置仓储（禁止内存兜底）
     * @param reader 实际事件读取器（禁止空实现兜底）
     * @param views 视图快照
     * @return 未启动运行时
     */
    public static <E> Ddd4jVertxRuntime createWithProjection(Vertx vertx,
            Collection<CommandExecutor<?>> executors, ProjectionPositionRepository repository,
            EventChunkReader<E> reader, Collection<? extends ProjectionView<E>> views) {
        ProjectionRunner<E> runner = projectionRunner(repository, reader);
        @SuppressWarnings("unchecked")
        Collection<ProjectionView<?>> actualViews = (Collection<ProjectionView<?>>) (Collection<?>) views;
        VertxProjectionViewManager manager = VertxProjectionViewManager.create(vertx, actualViews, runner);
        return attachProjection(vertx, executors, manager);
    }

    /**
     * 使用调用方借用的调度器创建显式持久投影；可在完整 Runner 任务边界实施应用排空。
     * 关闭只取消本运行时的 handles，不关闭借用调度器，也不等待已进入的任务。
     * @param <E> 事件类型
     * @param vertx 调用方原生实例
     * @param executors 命令执行器
     * @param repository 持久位置仓储
     * @param reader 实际事件读取器
     * @param views 视图快照
     * @param scheduler 调用方拥有并负责关闭的调度器
     * @return 未启动运行时
     */
    public static <E> Ddd4jVertxRuntime createWithProjection(Vertx vertx,
            Collection<CommandExecutor<?>> executors, ProjectionPositionRepository repository,
            EventChunkReader<E> reader, Collection<? extends ProjectionView<E>> views, ViewScheduler scheduler) {
        ProjectionRunner<E> runner = projectionRunner(repository, reader);
        Objects.requireNonNull(scheduler, "Explicit projection requires a scheduler");
        @SuppressWarnings("unchecked")
        Collection<ProjectionView<?>> actualViews = (Collection<ProjectionView<?>>) (Collection<?>) views;
        VertxProjectionViewManager manager = new VertxProjectionViewManager(scheduler, runner, actualViews);
        return attachProjection(vertx, executors, manager);
    }

    private static <E> ProjectionRunner<E> projectionRunner(ProjectionPositionRepository repository,
            EventChunkReader<E> reader) {
        Objects.requireNonNull(repository, "Explicit projection requires a position repository");
        Objects.requireNonNull(reader, "Explicit projection requires an event reader");
        if (repository instanceof InMemoryProjectionPositionRepository || reader instanceof NoopEventChunkReader<?>) {
            throw new IllegalArgumentException("Explicit durable projection requires a real reader and persistent repository");
        }
        return new ProjectionRunner<>(new DefaultProjectionService(repository), reader);
    }

    private static Ddd4jVertxRuntime attachProjection(Vertx vertx,
            Collection<CommandExecutor<?>> executors, VertxProjectionViewManager manager) {
        try {
            Ddd4jVertxRuntime runtime = create(vertx, executors);
            runtime.projectionManager = manager;
            return runtime;
        } catch (RuntimeException failure) {
            manager.close();
            throw failure;
        }
    }

    /** @return 显式启用时的唯一管理器；基础模式为空 */
    public Optional<VertxProjectionViewManager> projectionManager() {
        return Optional.ofNullable(projectionManager);
    }

    /** 在原生 worker 上执行一次投影，Future 保留失败；不阻塞事件循环。
     * @return 本轮投影完成结果
     */
    public Future<Void> triggerProjections() {
        if (Objects.isNull(projectionManager)) {
            return Future.failedFuture(new IllegalStateException("Projection is not enabled"));
        }
        return executeBlocking(() -> {
            projectionManager.triggerOnce();
            return null;
        });
    }

    /**
     * 获取运行时就绪注册表。
     *
     * @return 就绪注册表
     */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /**
     * 启动全局 SPI 与主体注册。
     */
    public void start() {
        try {
            if (Objects.nonNull(projectionManager)) {
                projectionManager.start();
            }
            registrations.start();
            subjectRegistration.start();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * 将当前线程上下文传播到 Vert.x 阻塞任务。
     *
     * @param <T> 任务结果类型
     * @param task 待执行任务
     * @return 异步任务结果
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

    @Override
    public void close() {
        try {
            if (Objects.nonNull(projectionManager)) {
                projectionManager.close();
            }
        } finally {
            try {
                subjectRegistration.close();
            } finally {
                registrations.close();
            }
        }
    }
}
