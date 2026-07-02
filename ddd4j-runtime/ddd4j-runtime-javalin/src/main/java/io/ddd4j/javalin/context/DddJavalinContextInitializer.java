/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */
package io.ddd4j.javalin.context;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.domain.event.DomainEventPublisher;
import io.ddd4j.core.domain.event.MQEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
/**
 * ddd4j Javalin 启动期 SPI 注入器。
 * <p>
 * 在 Javalin 启动前调用，将 CDI/DI 容器中的 ddd4j 核心 SPI 实例注入到 {@link BaseContext}，
 * 与 Spring/Quarkus/Guice 适配层保持完全一致的行为。
 * <p>
 * 使用示例：
 * <pre>{@code
 * Javalin app = Javalin.create(cfg -> {
 *     cfg.events(new DddJavalinContextInitializer(
 *         domainEventPublisher, mqEventPublisher, subjectProvider, i18nProvider));
 * });
 * }</pre>
 *
 * <h3>框架无关性保证</h3>
 * 本类不依赖任何 DI 容器（与 Guice/Spring 不同），由业务方手动传入 SPI 实例。
 * 业务方可与任何 DI 框架集成（如 Javalin 官方插件、Airline、Guice）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.0
 */
@Slf4j
public class DddJavalinContextInitializer implements Consumer<Javalin> {

    private final DomainEventPublisher domainEventPublisher;
    private final MQEventPublisher mqEventPublisher;
    private final SubjectProvider subjectProvider;
    private final I18nProvider i18nProvider;

    public DddJavalinContextInitializer(
            DomainEventPublisher domainEventPublisher,
            MQEventPublisher mqEventPublisher,
            SubjectProvider subjectProvider,
            I18nProvider i18nProvider) {
        this.domainEventPublisher = domainEventPublisher;
        this.mqEventPublisher = mqEventPublisher;
        this.subjectProvider = subjectProvider;
        this.i18nProvider = i18nProvider;
    }

    @Override
    public void accept(Javalin javalin) {
        log.info("Initializing ddd4j SPI services for Javalin");

        // DomainEventPublisher（必需）
        if (domainEventPublisher != null) {
            BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, domainEventPublisher);
        } else {
            log.warn("DomainEventPublisher is null. DomainEvent.publish() will fail.");
        }

        // MQEventPublisher（可选）
        if (mqEventPublisher != null) {
            BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, mqEventPublisher);
        } else {
            log.debug("MQEventPublisher is null. MQEvent.publish() will fail.");
        }

        // SubjectProvider（可选）
        if (subjectProvider != null) {
            BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
        }

        // I18nProvider（可选）
        if (i18nProvider != null) {
            BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider);
        }

        log.info("ddd4j SPI services initialized for Javalin (DomainEventPublisher={}, MQEventPublisher={})",
                domainEventPublisher != null, mqEventPublisher != null);
    }
}
