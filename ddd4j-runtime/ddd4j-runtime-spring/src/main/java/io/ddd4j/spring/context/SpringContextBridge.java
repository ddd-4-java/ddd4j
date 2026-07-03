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
package io.ddd4j.spring.context;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.event.MQEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring 启动期 SPI 注入器：将 Spring 容器中的 SPI Bean 注入到 ddd4j 上下文。
 * <p>
 * 替代旧的 {@code DomainEvent.registerPublisher} 静态注册模式，
 * 业务方调用 {@code new OrderCreatedEvent().publish()} 时通过 {@link Contexts}
 * 自动按「线程优先 → 全局兜底」策略查找 publisher。
 * <p>
 * 使用 {@link ObjectProvider} 而非直接注入 SPI 类型，避免业务方未提供某 SPI 时启动失败。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class SpringContextBridge implements ApplicationListener<ContextRefreshedEvent> {

    /** 领域事件发布器提供者 */
    private final ObjectProvider<DomainEventPublisher> domainEventPublisherProvider;
    /** MQ 事件发布器提供者 */
    private final ObjectProvider<MQEventPublisher> mqEventPublisherProvider;
    /** Subject 提供者 */
    private final ObjectProvider<SubjectProvider> subjectProviderProvider;
    /** 国际化提供者 */
    private final ObjectProvider<I18nProvider> i18nProviderProvider;

    public SpringContextBridge(
            ObjectProvider<DomainEventPublisher> domainEventPublisherProvider,
            ObjectProvider<MQEventPublisher> mqEventPublisherProvider,
            ObjectProvider<SubjectProvider> subjectProviderProvider,
            ObjectProvider<I18nProvider> i18nProviderProvider) {
        this.domainEventPublisherProvider = domainEventPublisherProvider;
        this.mqEventPublisherProvider = mqEventPublisherProvider;
        this.subjectProviderProvider = subjectProviderProvider;
        this.i18nProviderProvider = i18nProviderProvider;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // DomainEventPublisher（必需）
        DomainEventPublisher domainPublisher = domainEventPublisherProvider.getIfAvailable();
        if (Objects.nonNull(domainPublisher)) {
            BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, domainPublisher);
        } else {
            log.warn("No DomainEventPublisher bean found. DomainEvent.publish() will fail.");
        }

        // MQEventPublisher（可选）
        MQEventPublisher mqPublisher = mqEventPublisherProvider.getIfAvailable();
        if (Objects.nonNull(mqPublisher)) {
            BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, mqPublisher);
        } else {
            log.debug("No MQEventPublisher bean found. MQEvent.publish() will fail.");
        }

        // SubjectProvider（可选）
        SubjectProvider subjectProvider = subjectProviderProvider.getIfAvailable();
        if (Objects.nonNull(subjectProvider)) {
            BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
        }

        // I18nProvider（可选）
        I18nProvider i18nProvider = i18nProviderProvider.getIfAvailable();
        if (Objects.nonNull(i18nProvider)) {
            BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider);
        }

        log.info("Spring ddd4j SPI services initialized (DomainEventPublisher={}, MQEventPublisher={})",
                Objects.nonNull(domainPublisher), Objects.nonNull(mqPublisher));
    }
}
