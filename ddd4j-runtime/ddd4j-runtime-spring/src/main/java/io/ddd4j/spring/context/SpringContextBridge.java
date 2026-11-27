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
    /**
     * 管理当前示例或测试所拥有的 SPI 注册资源。
     */
    private SpiRegistrationScope registrationScope;
    /**
     * 当前桥接装配到兼容主体入口的框架提供器。
     */
    private SubjectProvider registeredSubjectProvider;
    /**
     * 为认证主体提供角色、权限等业务数据的组件。
     */
    private SubjectDataProvider registeredSubjectDataProvider;
    /**
     * 安装框架主体提供器前已存在的全局提供器。
     */
    private SubjectProvider previousSubjectProvider;
    /**
     * 进入当前执行范围前保存的组件，退出时用于恢复 <code>SubjectDataProvider</code>。
     */
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
     * 在 Spring 刷新完成时把当前应用服务接入 DDD4J 共享上下文。
     *
     * @param event 当前框架生命周期或容器启动事件
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

    /**
     * 在 Bean 销毁时撤销当前桥接拥有的运行时注册。
     */
    @Override
    public synchronized void destroy() {
        if (Objects.nonNull(registrationScope)) {
            registrationScope.close();
            registrationScope = null;
        }
        restoreLegacySubjectFacade();
    }

    /**
     * 按当前主体及其数据提供器装配兼容调用所用的主体入口。
     *
     * @param subjectProvider     从当前认证框架解析主体的组件
     * @param subjectDataProvider 提供角色和权限数据的桥接组件
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

    /**
     * 当共享主体入口仍归当前桥接所有时恢复旧提供器。
     */
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
