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
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
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
import java.util.concurrent.Callable;

/**
 * Vert.x 显式运行时。调用方拥有 Vertx 实例，本类不会代为关闭它。
 */
public final class Ddd4jVertxRuntime implements AutoCloseable {

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
        VertxDomainEventPublisher publisher = new VertxDomainEventPublisher(vertx);
        SubjectProvider subjectProvider = new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        return new Ddd4jVertxRuntime(vertx, publisher, subjectProvider, I18nProvider.DEFAULT,
                new DefaultCommandBus(executors));
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
        registrations.start();
        subjectRegistration.start();
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
        subjectRegistration.close();
        registrations.close();
    }
}
