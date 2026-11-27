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

    /** \u8d1f\u8d23\u5728\u5e94\u7528\u751f\u547d\u5468\u671f\u5185\u7ba1\u7406\u6295\u5f71\u4efb\u52a1\u7684\u7ec4\u4ef6\u3002 */
    private final DropwizardProjectionViewManager projectionManager;
    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u7ba1\u7406\u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668\u53ca\u5176\u6062\u590d\u52a8\u4f5c\u3002 */
    private final SubjectKitRegistrationScope subjectRegistration;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
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

    /** \u542f\u52a8 DDD4J \u8fd0\u884c\u65f6\u5e76\u63a5\u5165\u5df2\u88c5\u914d\u7684\u6295\u5f71\u7ba1\u7406\u5668\u3002 */
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

    /** \u505c\u6b62\u6295\u5f71\u4efb\u52a1\u5e76\u5173\u95ed\u5f53\u524d\u8fd0\u884c\u65f6\u3002 */
    @Override
    public void stop() {
        close();
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>Dropwizard 实现调用 <code>projectionManager.stop()</code>。</p>
     */
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
