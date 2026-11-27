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
import java.util.List;
import java.util.Objects;

/**
 * Micronaut 容器与 ddd4j 全局 SPI 的生命周期桥梁。
 */
public final class Ddd4jMicronautRuntime implements AutoCloseable {

    /** \u7ba1\u7406\u67e5\u8be2\u89c6\u56fe\u53ca\u5176\u6295\u5f71\u6267\u884c\u7684\u7ec4\u4ef6\u3002 */
    private final ViewManager viewManager;
    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u7ba1\u7406\u5f53\u524d\u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668\u53ca\u5176\u6062\u590d\u52a8\u4f5c\u3002 */
    private final SubjectKitRegistrationScope subjectRegistration;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
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
        this(publisher, subjectProvider, i18nProvider, commandBus, List.of());
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
     * \u5e73\u53f0\u5de5\u5382\u5728\u4f9d\u8d56\u9a8c\u8bc1\u540e\u63a5\u5165\u552f\u4e00\u89c6\u56fe\u751f\u547d\u5468\u671f\u3002
     *
     * @param publisher \u8fd0\u884c\u65f6\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668
     * @param subjectProvider \u5f53\u524d\u5e94\u7528\u7684\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668
     * @param i18nProvider \u56fd\u9645\u5316\u6d88\u606f\u63d0\u4f9b\u5668
     * @param commandBus \u8fd0\u884c\u65f6\u547d\u4ee4\u603b\u7ebf
     * @param readinessContributors \u63d0\u4f9b\u5e94\u7528\u5c31\u7eea\u72b6\u6001\u7684\u7ec4\u4ef6\u96c6\u5408
     * @param viewManager \u67e5\u8be2\u89c6\u56fe\u548c\u6295\u5f71\u7ba1\u7406\u5668
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

    /** {@inheritDoc} */
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
