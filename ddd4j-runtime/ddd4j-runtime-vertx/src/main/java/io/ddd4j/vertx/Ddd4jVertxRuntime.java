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
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import io.ddd4j.vertx.command.VertxCommandBus;
import io.ddd4j.vertx.cqrs.VertxProjectionViewManager;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Vert.x 显式运行时。调用方拥有 Vertx 实例，本类不会代为关闭它。
 */
public final class Ddd4jVertxRuntime implements AutoCloseable {

    /** \u5f53\u524d\u6846\u67b6\u7ec4\u4ef6\u5171\u4eab\u7684 Vert.x \u5b9e\u4f8b\u3002 */
    private final Vertx vertx;
    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u7ba1\u7406\u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668\u53ca\u5176\u6062\u590d\u52a8\u4f5c\u3002 */
    private final SubjectKitRegistrationScope subjectRegistration;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;
    /** \u8d1f\u8d23\u5728\u5e94\u7528\u751f\u547d\u5468\u671f\u5185\u7ba1\u7406\u6295\u5f71\u4efb\u52a1\u7684\u7ec4\u4ef6\u3002 */
    private VertxProjectionViewManager projectionManager;

    /**
     * 使用默认就绪检查集合创建显式运行时。
     *
     * @param vertx           调用方持有的 Vertx 实例
     * @param publisher       领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider    国际化提供器
     * @param commandBus      命令总线
     */
    public Ddd4jVertxRuntime(Vertx vertx, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(vertx, publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * 创建带就绪检查的显式运行时。
     *
     * @param vertx                 调用方持有的 Vertx 实例
     * @param publisher             领域事件发布器
     * @param subjectProvider       主体提供器
     * @param i18nProvider          国际化提供器
     * @param commandBus            命令总线
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
     * @param vertx     调用方持有的 Vertx 实例
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

    /**
     * 显式创建持久投影运行时；缺少实际 reader/store 时在发布 SPI 或注册 timer 前拒绝。
     * 存储和 Vertx 均由调用方拥有；不推导 ORM、事务或业务 schema。
     *
     * @param <E>        事件类型
     * @param vertx      调用方原生实例
     * @param executors  命令执行器
     * @param repository 持久位置仓储（禁止内存兜底）
     * @param reader     实际事件读取器（禁止空实现兜底）
     * @param views      视图快照
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
     *
     * @param <E>        事件类型
     * @param vertx      调用方原生实例
     * @param executors  命令执行器
     * @param repository 持久位置仓储
     * @param reader     实际事件读取器
     * @param views      视图快照
     * @param scheduler  调用方拥有并负责关闭的调度器
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

    /**
     * \u5c06\u4e8b\u4ef6\u8bfb\u53d6\u7aef\u53e3\u4e0e\u5171\u4eab\u6295\u5f71\u6267\u884c\u670d\u52a1\u88c5\u914d\u4e3a\u8fd0\u884c\u5668\u3002
     * @param <E> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param repository \u4fdd\u5b58\u5e76\u8bfb\u53d6\u6295\u5f71\u68c0\u67e5\u70b9\u7684\u4ed3\u50a8
     * @param reader \u4ece\u4e8b\u4ef6\u6e90\u6309\u4f4d\u70b9\u5206\u6279\u8bfb\u53d6\u4e8b\u4ef6\u7684\u7ec4\u4ef6
     * @return \u6267\u884c\u5df2\u6ce8\u518c\u6295\u5f71\u89c6\u56fe\u7684\u6295\u5f71\u8fd0\u884c\u5668
     */
    private static <E> ProjectionRunner<E> projectionRunner(ProjectionPositionRepository repository,
                                                            EventChunkReader<E> reader) {
        Objects.requireNonNull(repository, "Explicit projection requires a position repository");
        Objects.requireNonNull(reader, "Explicit projection requires an event reader");
        if (repository instanceof InMemoryProjectionPositionRepository || reader instanceof NoopEventChunkReader<?>) {
            throw new IllegalArgumentException("Explicit durable projection requires a real reader and persistent repository");
        }
        return new ProjectionRunner<>(new DefaultProjectionService(repository), reader);
    }

    /**
     * \u7ec4\u88c5\u8fde\u63a5 Vert.x \u5bb9\u5668\u7684 DDD4J \u8fd0\u884c\u65f6\u53ca\u6295\u5f71\u7ba1\u7406\u5668\u3002
     * @param vertx \u627f\u8f7d\u5f53\u524d\u8fd0\u884c\u65f6\u548c\u4e8b\u4ef6\u603b\u7ebf\u7684 Vert.x \u5b9e\u4f8b
     * @param executors \u5f53\u524d\u6846\u67b6\u5bb9\u5668\u63d0\u4f9b\u7684\u547d\u4ee4\u6267\u884c\u5668
     * @param manager \u7528\u4e8e\u8c03\u5ea6\u5f53\u524d\u89c6\u56fe\u7684\u7ba1\u7406\u5668
     * @return \u5305\u542b\u89c6\u56fe\u7ba1\u7406\u5668\u7684 Vert.x \u8fd0\u884c\u65f6
     */
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

    /**
     * @return 显式启用时的唯一管理器；基础模式为空
     */
    public Optional<VertxProjectionViewManager> projectionManager() {
        return Optional.ofNullable(projectionManager);
    }

    /**
     * 在原生 worker 上执行一次投影，Future 保留失败；不阻塞事件循环。
     *
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
     * @param <T>  任务结果类型
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

    /** {@inheritDoc} */
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
