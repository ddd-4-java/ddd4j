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
package io.ddd4j.guice;

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Injector;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.guice.cqrs.GuiceViewManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Guice 容器与 ddd4j 全局 SPI 的生命周期桥梁。
 */
public final class Ddd4jGuiceRuntime implements AutoCloseable {

    /** \u4e3a\u5f53\u524d Guice \u8fd0\u884c\u65f6\u63d0\u4f9b\u670d\u52a1\u7684\u6ce8\u5165\u5668\u3002 */
    private final Injector injector;
    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private final SpiRegistrationScope registrations;
    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * 使用默认就绪检查集合创建 Guice 运行时。
     *
     * @param injector Guice 注入器
     * @param publisher 领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider 国际化提供器
     * @param commandBus 命令总线
     */
    @Inject
    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(injector, publisher, subjectProvider, i18nProvider, commandBus, Collections.emptyList());
    }

    /**
     * 使用指定就绪检查集合创建 Guice 运行时。
     *
     * @param injector Guice 注入器
     * @param publisher 领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider 国际化提供器
     * @param commandBus 命令总线
     * @param readinessContributors 就绪检查贡献者
     */
    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus,
                             Collection<? extends ReadinessContributor> readinessContributors) {
        this.injector = Objects.requireNonNull(injector, "injector must not be null");
        registrations = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                        Objects.requireNonNull(publisher, "publisher must not be null"))
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                        Objects.requireNonNull(subjectProvider, "subjectProvider must not be null"))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                        Objects.requireNonNull(i18nProvider, "i18nProvider must not be null"))
                .register(SpiKeys.COMMAND_BUS, CommandBus.class,
                        Objects.requireNonNull(commandBus, "commandBus must not be null"));
        readinessRegistry = new RuntimeReadinessRegistry(readinessContributors);
        start();
        GuiceContext.setInjector(this.injector);
    }

    /** 启动 ddd4j 全局 SPI 注册。 */
    public void start() {
        registrations.start();
    }

    /**
     * 获取运行时就绪注册表。
     *
     * @return 就绪注册表
     */
    public RuntimeReadinessRegistry readiness() {
        return readinessRegistry;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        try {
            Binding<GuiceViewManager> binding = injector.getExistingBinding(Key.get(GuiceViewManager.class));
            if (Objects.nonNull(binding)) {
                binding.getProvider().get().close();
            }
        } finally {
            registrations.close();
            GuiceContext.clear();
        }
    }
}
