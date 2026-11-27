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
package io.ddd4j.quarkus;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.health.RuntimeReadinessRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Quarkus CDI 生命周期使用的 ddd4j 全局 SPI 注册作用域。
 */
public final class Ddd4jQuarkusRuntime implements AutoCloseable {

    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * 使用默认就绪检查项创建运行时。
     *
     * @param publisher 领域事件发布器
     * @param subjectProvider 主体提供者
     * @param i18nProvider 国际化提供者
     * @param commandBus 命令总线
     */
    public Ddd4jQuarkusRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus) {
        this(publisher, subjectProvider, i18nProvider, commandBus, Collections.emptyList());
    }

    /**
     * 使用指定就绪检查项创建运行时。
     *
     * @param publisher 领域事件发布器
     * @param subjectProvider 主体提供者
     * @param i18nProvider 国际化提供者
     * @param commandBus 命令总线
     * @param readinessContributors 就绪检查项
     */
    public Ddd4jQuarkusRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus,
                               Collection<? extends ReadinessContributor> readinessContributors) {
        this(publisher, subjectProvider, i18nProvider, commandBus,
                new RuntimeReadinessRegistry(readinessContributors));
    }

    /**
     * 使用就绪注册表创建运行时并注册框架 SPI。
     *
     * @param publisher 领域事件发布器
     * @param subjectProvider 主体提供者
     * @param i18nProvider 国际化提供者
     * @param commandBus 命令总线
     * @param readinessRegistry 就绪状态注册表
     */
    public Ddd4jQuarkusRuntime(DomainEventPublisher publisher, SubjectProvider subjectProvider,
                               I18nProvider i18nProvider, CommandBus commandBus,
                               RuntimeReadinessRegistry readinessRegistry) {
        registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                        Objects.requireNonNull(publisher, "publisher must not be null"))
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                        Objects.requireNonNull(subjectProvider, "subjectProvider must not be null"))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                        Objects.requireNonNull(i18nProvider, "i18nProvider must not be null"))
                .register(SpiKeys.COMMAND_BUS, CommandBus.class,
                        Objects.requireNonNull(commandBus, "commandBus must not be null"));
        this.readinessRegistry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
    }

    /** 启动框架 SPI 注册作用域。 */
    public void start() {
        registrations.start();
    }

    /** @return 运行时就绪状态注册表 */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        registrations.close();
    }
}
