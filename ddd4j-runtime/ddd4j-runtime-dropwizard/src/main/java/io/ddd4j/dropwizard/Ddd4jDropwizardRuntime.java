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
package io.ddd4j.dropwizard;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKitRegistrationScope;
import io.ddd4j.dropwizard.cqrs.DropwizardProjectionViewManager;
import io.dropwizard.lifecycle.Managed;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dropwizard Managed 生命周期中的 ddd4j SPI 注册器。
 */
public final class Ddd4jDropwizardRuntime implements Managed, AutoCloseable {

    private final DropwizardProjectionViewManager projectionManager;
    private final SpiRegistrationScope registrations;
    private final SubjectKitRegistrationScope subjectRegistration;
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * 使用默认就绪检查集合创建运行时。
     *
     * @param publisher       领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider    国际化提供器
     * @param commandBus      命令总线
     */
    public Ddd4jDropwizardRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                  I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * 使用指定就绪检查集合创建运行时。
     *
     * @param publisher             领域事件发布器
     * @param subjectProvider       主体提供器
     * @param i18nProvider          国际化提供器
     * @param commandBus            命令总线
     * @param readinessContributors 就绪检查贡献者
     */
    public Ddd4jDropwizardRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                  I18nProvider i18nProvider, CommandBus commandBus,
                                  Collection<? extends ReadinessContributor> readinessContributors) {
        this(publisher, subjectProvider, i18nProvider, commandBus, readinessContributors, null);
    }

    /**
     * 创建可选投影的原生 Managed；不取得调用方服务的关闭权限。
     *
     * @param publisher             领域事件发布器
     * @param subjectProvider       主体提供器
     * @param i18nProvider          国际化提供器
     * @param commandBus            命令总线
     * @param readinessContributors 就绪贡献者
     * @param projectionManager     可选投影管理器
     */
    public Ddd4jDropwizardRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                                  I18nProvider i18nProvider, CommandBus commandBus,
                                  Collection<? extends ReadinessContributor> readinessContributors,
                                  DropwizardProjectionViewManager projectionManager) {
        this.projectionManager = projectionManager;
        this.registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher)
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider)
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider)
                .register(SpiKeys.COMMAND_BUS, CommandBus.class, commandBus);
        this.subjectRegistration = new SubjectKitRegistrationScope(subjectProvider);
        this.readinessRegistry = new RuntimeReadinessRegistry(readinessContributors);
    }

    @Override
    public synchronized void start() {
        try {
            registrations.start();
            subjectRegistration.start();
            if (Objects.nonNull(projectionManager)) {
                projectionManager.start();
            }
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
     * @return 显式投影管理器；基础模式为空且不创建任何投影资源
     */
    public Optional<DropwizardProjectionViewManager> projectionManager() {
        return Optional.ofNullable(projectionManager);
    }

    /**
     * 获取运行时就绪注册表。
     *
     * @return 就绪注册表
     */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public synchronized void close() {
        try {
            if (Objects.nonNull(projectionManager)) {
                projectionManager.stop();
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
