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
import io.ddd4j.core.health.RuntimeReadinessRegistry;
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

    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry = new RuntimeReadinessRegistry();
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
    /** \u4ece\u5f53\u524d\u6846\u67b6\u89e3\u6790\u547d\u4ee4\u6267\u884c\u5668\u5e76\u8c03\u7528\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
    @Inject
    Instance<CommandBus> commandBus;
    /** \u53c2\u4e0e\u5e94\u7528\u5c31\u7eea\u72b6\u6001\u6c47\u603b\u7684\u8fd0\u884c\u7ec4\u4ef6\u3002 */
    @Inject
    Instance<ReadinessContributor> readinessContributors;
    /** \u5f53\u524d\u5bb9\u5668\u88c5\u914d\u5e76\u8d1f\u8d23\u5173\u95ed\u7684 DDD4J \u8fd0\u884c\u65f6\u3002 */
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
     * \u5e94\u7528\u542f\u52a8\u5b8c\u6210\u540e\u6ce8\u5165 SPI \u670d\u52a1\u5230\u4e0a\u4e0b\u6587\u3002
     *  <p>
     *  \u4f7f\u7528 {@link Instance} \u800c\u975e\u76f4\u63a5\u6ce8\u5165\uff0c\u907f\u514d\u4e1a\u52a1\u65b9\u672a\u63d0\u4f9b\u67d0 SPI \u65f6\u542f\u52a8\u5931\u8d25\u3002
     *
     * @param event Quarkus \u542f\u52a8\u4e8b\u4ef6
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
        readinessRegistry.registerAll(readinessContributors.stream().collect(java.util.stream.Collectors.toList()));
        runtime = new Ddd4jQuarkusRuntime(domainEventPublisher.get(), subjectProvider.get(), i18nProvider.get(),
                commandBus.get(), readinessRegistry);
        runtime.start();

        log.info("Quarkus ddd4j SPI services initialized");
    }

    /**
     * \u76d1\u542c\u5e94\u7528\u505c\u6b62\u4e8b\u4ef6\u5e76\u5173\u95ed DDD4J \u8fd0\u884c\u65f6\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    synchronized void onStop(@Observes ShutdownEvent event) {
        if (Objects.nonNull(runtime)) {
            runtime.close();
            runtime = null;
        }
    }
}
