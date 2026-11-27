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
import com.google.inject.Injector;
import com.google.inject.Key;
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
import java.util.List;
import java.util.Objects;

/**
 * Guice 容器与 ddd4j 全局 SPI 的生命周期桥梁。
 */
public final class Ddd4jGuiceRuntime implements AutoCloseable {

    /**
     * 为当前 Guice 运行时提供服务的注入器。
     */
    private final Injector injector;
    /**
     * 管理当前示例或测试所拥有的 SPI 注册资源。
     */
    private final SpiRegistrationScope registrations;
    /**
     * 汇总当前组件就绪状态的注册表。
     */
    private final RuntimeReadinessRegistry readinessRegistry;

    /**
     * 使用默认就绪检查集合创建 Guice 运行时。
     *
     * @param injector        Guice 注入器
     * @param publisher       领域事件发布器
     * @param subjectProvider 主体提供器
     * @param i18nProvider    国际化提供器
     * @param commandBus      命令总线
     */
    @Inject
    public Ddd4jGuiceRuntime(Injector injector, DomainEventPublisher publisher, SubjectProvider subjectProvider,
                             I18nProvider i18nProvider, CommandBus commandBus) {
        this(injector, publisher, subjectProvider, i18nProvider, commandBus, List.of());
    }

    /**
     * 使用指定就绪检查集合创建 Guice 运行时。
     *
     * @param injector              Guice 注入器
     * @param publisher             领域事件发布器
     * @param subjectProvider       主体提供器
     * @param i18nProvider          国际化提供器
     * @param commandBus            命令总线
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
        // 显式能力先完成启动；失败时其自身回滚线程和句柄，尚未发布任何本实例全局状态。
        Binding<Ddd4jProjectionGuiceModule.Startup> projectionStartup = injector.getExistingBinding(
                Key.get(Ddd4jProjectionGuiceModule.Startup.class));
        if (Objects.nonNull(projectionStartup)) {
            projectionStartup.getProvider().get();
        }
        start();
        GuiceContext.setInjector(this.injector);
    }

    /**
     * 启动 ddd4j 全局 SPI 注册。
     */
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

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>injector.getExistingBinding(Key.get(GuiceViewManager.class))</code>。</p>
     */
    @Override
    public void close() {
        try {
            Binding<GuiceViewManager> binding = injector.getExistingBinding(
                    Key.get(GuiceViewManager.class));
            if (Objects.nonNull(binding)) {
                binding.getProvider().get().close();
            }
        } finally {
            registrations.close();
            GuiceContext.clear();
        }
    }
}
