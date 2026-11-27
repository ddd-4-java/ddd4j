/*
 * Copyright 2017-2026 the original author redacted-legacy-family.
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
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
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
public class SpringContextBridge implements ApplicationListener<ContextRefreshedEvent>, DisposableBean {

    /**
     * 领域事件发布器提供者
     */
    private final ObjectProvider<DomainEventPublisher> domainEventPublisherProvider;
    /**
     * Subject 提供者
     */
    private final ObjectProvider<SubjectProvider> subjectProviderProvider;
    /**
     * 权限数据提供者
     */
    private final ObjectProvider<SubjectDataProvider> subjectDataProviderProvider;
    /**
     * 国际化提供者
     */
    private final ObjectProvider<I18nProvider> i18nProviderProvider;
    /**
     * 命令总线提供者
     */
    private final ObjectProvider<CommandBus> commandBusProvider;
    /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
    private SpiRegistrationScope registrationScope;
    /** \u5f53\u524d\u6865\u63a5\u88c5\u914d\u5230\u517c\u5bb9\u4e3b\u4f53\u5165\u53e3\u7684\u6846\u67b6\u63d0\u4f9b\u5668\u3002 */
    private SubjectProvider registeredSubjectProvider;
    /** \u4e3a\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u89d2\u8272\u3001\u6743\u9650\u7b49\u4e1a\u52a1\u6570\u636e\u7684\u7ec4\u4ef6\u3002 */
    private SubjectDataProvider registeredSubjectDataProvider;
    /** \u5b89\u88c5\u6846\u67b6\u4e3b\u4f53\u63d0\u4f9b\u5668\u524d\u5df2\u5b58\u5728\u7684\u5168\u5c40\u63d0\u4f9b\u5668\u3002 */
    private SubjectProvider previousSubjectProvider;
    /** \u8fdb\u5165\u5f53\u524d\u6267\u884c\u8303\u56f4\u524d\u4fdd\u5b58\u7684\u7ec4\u4ef6\uff0c\u9000\u51fa\u65f6\u7528\u4e8e\u6062\u590d <code>SubjectDataProvider</code>\u3002 */
    private SubjectDataProvider previousSubjectDataProvider;

    /**
     * 创建 Spring 服务到框架上下文的桥接器。
     *
     * @param domainEventPublisherProvider 领域事件发布器提供者
     * @param subjectProviderProvider      主体提供者
     * @param subjectDataProviderProvider  主体数据提供者
     * @param i18nProviderProvider         国际化提供者
     * @param commandBusProvider           命令总线提供者
     */
    public SpringContextBridge(
            ObjectProvider<DomainEventPublisher> domainEventPublisherProvider,
            ObjectProvider<SubjectProvider> subjectProviderProvider,
            ObjectProvider<SubjectDataProvider> subjectDataProviderProvider,
            ObjectProvider<I18nProvider> i18nProviderProvider,
            ObjectProvider<CommandBus> commandBusProvider) {
        this.domainEventPublisherProvider = domainEventPublisherProvider;
        this.subjectProviderProvider = subjectProviderProvider;
        this.subjectDataProviderProvider = subjectDataProviderProvider;
        this.i18nProviderProvider = i18nProviderProvider;
        this.commandBusProvider = commandBusProvider;
    }

    /**
     * \u5728 Spring \u5237\u65b0\u5b8c\u6210\u65f6\u628a\u5f53\u524d\u5e94\u7528\u670d\u52a1\u63a5\u5165 DDD4J \u5171\u4eab\u4e0a\u4e0b\u6587\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    @Override
    public synchronized void onApplicationEvent(ContextRefreshedEvent event) {
        if (Objects.nonNull(registrationScope)) {
            return;
        }

        SpiRegistrationScope scope = new SpiRegistrationScope();
        DomainEventPublisher domainPublisher = domainEventPublisherProvider.getIfAvailable();
        if (Objects.nonNull(domainPublisher)) {
            scope.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, domainPublisher);
        } else {
            log.warn("No DomainEventPublisher bean found. DomainEvent.publish() will fail.");
        }

        SubjectProvider subjectProvider = subjectProviderProvider.getIfAvailable();
        if (Objects.nonNull(subjectProvider)) {
            scope.register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
        }

        SubjectDataProvider subjectDataProvider = subjectDataProviderProvider.getIfAvailable();
        I18nProvider i18nProvider = i18nProviderProvider.getIfAvailable();
        if (Objects.nonNull(i18nProvider)) {
            scope.register(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider);
        }

        CommandBus commandBus = commandBusProvider.getIfAvailable();
        if (Objects.nonNull(commandBus)) {
            scope.register(SpiKeys.COMMAND_BUS, CommandBus.class, commandBus);
        }

        scope.start();
        registrationScope = scope;
        registerLegacySubjectFacade(subjectProvider, subjectDataProvider);
        log.info("Spring ddd4j SPI services initialized");
    }

    /** \u5728 Bean \u9500\u6bc1\u65f6\u64a4\u9500\u5f53\u524d\u6865\u63a5\u62e5\u6709\u7684\u8fd0\u884c\u65f6\u6ce8\u518c\u3002 */
    @Override
    public synchronized void destroy() {
        if (Objects.nonNull(registrationScope)) {
            registrationScope.close();
            registrationScope = null;
        }
        restoreLegacySubjectFacade();
    }

    /**
     * \u6309\u5f53\u524d\u4e3b\u4f53\u53ca\u5176\u6570\u636e\u63d0\u4f9b\u5668\u88c5\u914d\u517c\u5bb9\u8c03\u7528\u6240\u7528\u7684\u4e3b\u4f53\u5165\u53e3\u3002
     * @param subjectProvider \u4ece\u5f53\u524d\u8ba4\u8bc1\u6846\u67b6\u89e3\u6790\u4e3b\u4f53\u7684\u7ec4\u4ef6
     * @param subjectDataProvider \u63d0\u4f9b\u89d2\u8272\u548c\u6743\u9650\u6570\u636e\u7684\u6865\u63a5\u7ec4\u4ef6
     */
    private void registerLegacySubjectFacade(SubjectProvider subjectProvider, SubjectDataProvider subjectDataProvider) {
        if (Objects.nonNull(subjectProvider)) {
            previousSubjectProvider = SubjectKit.subjectProvider;
            registeredSubjectProvider = subjectProvider;
            SubjectKit.register(subjectProvider);
        }
        if (Objects.nonNull(subjectDataProvider)) {
            previousSubjectDataProvider = SubjectKit.dataProvider;
            registeredSubjectDataProvider = subjectDataProvider;
            SubjectKit.setDataProvider(subjectDataProvider);
        }
    }

    /** \u5f53\u5171\u4eab\u4e3b\u4f53\u5165\u53e3\u4ecd\u5f52\u5f53\u524d\u6865\u63a5\u6240\u6709\u65f6\u6062\u590d\u65e7\u63d0\u4f9b\u5668\u3002 */
    private void restoreLegacySubjectFacade() {
        if (Objects.equals(SubjectKit.subjectProvider, registeredSubjectProvider)) {
            SubjectKit.register(previousSubjectProvider);
        }
        if (Objects.equals(SubjectKit.dataProvider, registeredSubjectDataProvider)) {
            SubjectKit.setDataProvider(previousSubjectDataProvider);
        }
        registeredSubjectProvider = null;
        registeredSubjectDataProvider = null;
        previousSubjectProvider = null;
        previousSubjectDataProvider = null;
    }
}
