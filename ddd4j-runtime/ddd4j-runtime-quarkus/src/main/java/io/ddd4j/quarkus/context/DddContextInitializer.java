/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.quarkus.context;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.event.MQEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

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
     * MQ 事件发布器实例
     */
    @Inject
    Instance<MQEventPublisher> mqEventPublisher;

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

    /**
     * 应用启动完成后注入 SPI 服务到上下文。
     * <p>
     * 使用 {@link Instance} 而非直接注入，避免业务方未提供某 SPI 时启动失败。
     */
    void onStart(@Observes StartupEvent event) {
        // DomainEventPublisher（必需）
        if (domainEventPublisher.isUnsatisfied()) {
            log.warn("No DomainEventPublisher bean found. DomainEvent.publish() will fail.");
        } else {
            BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                    domainEventPublisher.get());
        }

        // MQEventPublisher（可选）
        if (!mqEventPublisher.isUnsatisfied()) {
            BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class,
                    mqEventPublisher.get());
        } else {
            log.debug("No MQEventPublisher bean found. MQEvent.publish() will fail.");
        }

        // SubjectProvider（可选）
        if (!subjectProvider.isUnsatisfied()) {
            BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                    subjectProvider.get());
        }

        // I18nProvider（可选）
        if (!i18nProvider.isUnsatisfied()) {
            BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                    i18nProvider.get());
        }

        log.info("Quarkus ddd4j SPI services initialized");
    }
}
