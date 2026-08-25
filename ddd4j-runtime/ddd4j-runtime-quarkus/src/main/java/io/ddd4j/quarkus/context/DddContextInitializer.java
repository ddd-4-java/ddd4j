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
package io.ddd4j.quarkus.context;

import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.quarkus.Ddd4jQuarkusRuntime;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Quarkus 启动期 SPI 注入器：将 CDI Bean 中的 SPI 实现注入到 ddd4j 上下文。
 * <p>
 * 替代旧的 {@code DomainEvent.registerPublisher} 静态注册模式，
 * 业务方调用 {@code new OrderCreatedEvent().publish()} 时通过 {@link Contexts}
 * 自动按「线程优先 → 全局兜底」策略查找 publisher。
 * <p>
 * 使用示例（业务项目）：
 * <pre>{@code
 * &#64;ApplicationScoped
 * public class MyDomainEventPublisher implements DomainEventPublisher { ... }
 *
 * // 启动期自动注入，无需业务方手动注册
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
@ApplicationScoped
public class DddContextInitializer {

    /**
     * 领域事件发布器实例
     */
    @Inject
    Instance<DomainEventPublisher> domainEventPublisher;

    /**
     * Subject 提供者实例
     */
    @Inject
    Instance<SubjectProvider> subjectProvider;

    /**
     * 国际化提供者实例
     */
    @Inject
    Instance<I18nProvider> i18nProvider;
    @Inject
    Instance<CommandBus> commandBus;
    @Inject
    Instance<ReadinessContributor> readinessContributors;
    private final RuntimeReadinessRegistry readinessRegistry = new RuntimeReadinessRegistry();
    private Ddd4jQuarkusRuntime runtime;

    /**
     * 将同一个运行时 Registry 暴露给 SmallRye Health，避免健康检查与 SPI 生命周期使用不同实例。
     *
     * @return Quarkus 应用的就绪检查注册表
     */
    @Produces
    @ApplicationScoped
    RuntimeReadinessRegistry runtimeReadinessRegistry() {
        return readinessRegistry;
    }

    /**
     * 应用启动完成后注入 SPI 服务到上下文。
     * <p>
     * 使用 {@link Instance} 而非直接注入，避免业务方未提供某 SPI 时启动失败。
     */
    synchronized void onStart(@Observes StartupEvent event) {
        if (Objects.nonNull(runtime)) {
            return;
        }
        if (!domainEventPublisher.isResolvable() || !subjectProvider.isResolvable()
                || !i18nProvider.isResolvable() || !commandBus.isResolvable()) {
            log.warn("Ddd4j Quarkus runtime requires unique DomainEventPublisher, SubjectProvider, I18nProvider and CommandBus beans");
            return;
        }
        readinessRegistry.registerAll(readinessContributors.stream().toList());
        runtime = new Ddd4jQuarkusRuntime(domainEventPublisher.get(), subjectProvider.get(), i18nProvider.get(),
                commandBus.get(), readinessRegistry);
        runtime.start();

        log.info("Quarkus ddd4j SPI services initialized");
    }

    synchronized void onStop(@Observes ShutdownEvent event) {
        if (Objects.nonNull(runtime)) {
            runtime.close();
            runtime = null;
        }
    }
}
