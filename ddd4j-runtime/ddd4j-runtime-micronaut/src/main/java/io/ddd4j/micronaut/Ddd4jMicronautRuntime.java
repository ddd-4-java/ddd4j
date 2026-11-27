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
package io.ddd4j.micronaut;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Micronaut 容器与 ddd4j 全局 SPI 的生命周期桥梁。
 */
public final class Ddd4jMicronautRuntime implements AutoCloseable {

    /**
     * 管理查询视图及其投影执行的组件。
     */
    private final ViewManager viewManager;
    /**
     * 管理当前示例或测试所拥有的 SPI 注册资源。
     */
    private final SpiRegistrationScope registrations;
    /**
     * 管理当前运行时注册的认证主体提供器及其恢复动作。
     */
    private final SubjectKitRegistrationScope subjectRegistration;
    /**
     * 汇总当前组件就绪状态的注册表。
     */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * 使用默认的就绪检查集合创建运行时桥梁。
     *
     * @param publisher       领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider    国际化提供器
     * @param commandBus      命令总线
     */
    public Ddd4jMicronautRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                 I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, Collections.emptyList());
    }

    /**
     * 注册 Micronaut 提供的 ddd4j 服务与就绪检查。
     *
     * @param publisher             领域事件发布器
     * @param subjectProvider       主体提供器
     * @param i18nProvider          国际化提供器
     * @param commandBus            命令总线
     * @param readinessContributors 就绪检查贡献者
     */
    public Ddd4jMicronautRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                 I18nProvider i18nProvider, CommandBus commandBus,
                                 Collection<? extends ReadinessContributor> readinessContributors) {
        this(publisher, subjectProvider, i18nProvider, commandBus, readinessContributors, null);
    }

    /**
     * 平台工厂在依赖验证后接入唯一视图生命周期。
     *
     * @param publisher             运行时领域事件发布器
     * @param subjectProvider       当前应用的认证主体提供器
     * @param i18nProvider          国际化消息提供器
     * @param commandBus            运行时命令总线
     * @param readinessContributors 提供应用就绪状态的组件集合
     * @param viewManager           查询视图和投影管理器
     */
    Ddd4jMicronautRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                          I18nProvider i18nProvider, CommandBus commandBus,
                          Collection<? extends ReadinessContributor> readinessContributors, ViewManager viewManager) {
        this.viewManager = viewManager;
        this.registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                        Objects.requireNonNull(publisher, "publisher must not be null"))
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                        Objects.requireNonNull(subjectProvider, "subjectProvider must not be null"))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                        Objects.requireNonNull(i18nProvider, "i18nProvider must not be null"))
                .register(SpiKeys.COMMAND_BUS, CommandBus.class,
                        Objects.requireNonNull(commandBus, "commandBus must not be null"));
        this.subjectRegistration = new SubjectKitRegistrationScope(subjectProvider);
        this.readinessRegistry = new RuntimeReadinessRegistry(readinessContributors);
    }

    /**
     * 启动全局 SPI 和主体注册。
     */
    @PostConstruct
    public void start() {
        try {
            if (Objects.nonNull(viewManager)) {
                viewManager.start();
            }
            registrations.start();
            subjectRegistration.start();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                if (failure != cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    /**
     * 获取当前运行时的就绪注册表。
     *
     * @return 就绪注册表
     */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Micronaut 实现调用 <code>viewManager.stop()</code>。</p>
     */
    @Override
    @PreDestroy
    public void close() {
        try {
            if (Objects.nonNull(viewManager)) {
                viewManager.stop();
            }
        } finally {
            subjectRegistration.close();
            registrations.close();
        }
    }
}
