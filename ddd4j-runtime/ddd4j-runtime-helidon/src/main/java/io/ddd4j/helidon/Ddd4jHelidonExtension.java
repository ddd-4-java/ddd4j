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
package io.ddd4j.helidon;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import jakarta.inject.Singleton;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Helidon MP 启动和关闭阶段的 ddd4j SPI CDI Extension。
 */
@Slf4j
public final class Ddd4jHelidonExtension implements Extension {

    /** \u6c47\u603b\u5f53\u524d\u7ec4\u4ef6\u5c31\u7eea\u72b6\u6001\u7684\u6ce8\u518c\u8868\u3002 */
    private final RuntimeReadinessRegistry readinessRegistry = new RuntimeReadinessRegistry();
    /** \u5f53\u524d\u5bb9\u5668\u88c5\u914d\u5e76\u8d1f\u8d23\u5173\u95ed\u7684 DDD4J \u8fd0\u884c\u65f6\u3002 */
    private Ddd4jHelidonRuntime runtime;

    /**
     * \u4e3a CDI \u751f\u547d\u5468\u671f\u6ce8\u518c DDD4J \u5c31\u7eea\u68c0\u67e5\u6ce8\u518c\u8868\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void registerReadinessRegistry(@Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(RuntimeReadinessRegistry.class)
                .scope(Singleton.class)
                .createWith(context -> readinessRegistry);
    }

    /**
     * \u5728 CDI \u90e8\u7f72\u5b8c\u6210\u540e\u521b\u5efa\u5e76\u542f\u52a8 DDD4J \u8fd0\u884c\u65f6\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     */
    void start(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);
        SubjectProvider subjectProvider = bean(beanManager, SubjectProvider.class);
        if (Objects.isNull(subjectProvider)) {
            subjectProvider = new InMemorySubjectProvider(new InMemorySubject(publisher::publish));
        }
        I18nProvider i18nProvider = bean(beanManager, I18nProvider.class);
        if (Objects.isNull(i18nProvider)) {
            i18nProvider = I18nProvider.DEFAULT;
        }
        CommandBus commandBus = bean(beanManager, CommandBus.class);
        if (Objects.isNull(commandBus)) {
            commandBus = new DefaultCommandBus(commandExecutors(beanManager));
        }
        readinessRegistry.registerAll(readinessContributors(beanManager));
        runtime = new Ddd4jHelidonRuntime(publisher, subjectProvider, i18nProvider, commandBus,
                readinessRegistry);
        runtime.start();
        log.info("ddd4j Helidon runtime initialized");
    }

    /**
     * \u5728\u5bb9\u5668\u5173\u95ed\u524d\u505c\u6b62\u8fd0\u884c\u65f6\u3001\u6295\u5f71\u5e76\u6e05\u7406\u5df2\u83b7\u53d6\u5f15\u7528\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void stop(@Observes BeforeShutdown event) {
        if (Objects.nonNull(runtime)) {
            runtime.close();
        }
    }

    /**
     * \u4ece CDI \u5bb9\u5668\u67e5\u627e\u5e76\u6536\u96c6\u547d\u4ee4\u6267\u884c\u5668\u3002
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @return \u5bb9\u5668\u4e2d\u53ef\u89e3\u6790\u7684\u547d\u4ee4\u6267\u884c\u5668\u96c6\u5408
     */
    private List<CommandExecutor<?>> commandExecutors(BeanManager beanManager) {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(CommandExecutor.class)) {
            CommandExecutor<?> executor = reference(beanManager, bean, CommandExecutor.class);
            executors.add(executor);
        }
        return executors;
    }

    /**
     * \u4ece CDI \u627e\u5230\u53ef\u53c2\u4e0e\u5e94\u7528\u5c31\u7eea\u68c0\u67e5\u7684\u8d21\u732e\u8005\u3002
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @return \u5e94\u7528\u7684\u5c31\u7eea\u68c0\u67e5\u8d21\u732e\u8005\u96c6\u5408
     */
    private List<ReadinessContributor> readinessContributors(BeanManager beanManager) {
        List<ReadinessContributor> contributors = new ArrayList<>();
        for (Bean<?> bean : beanManager.getBeans(ReadinessContributor.class)) {
            contributors.add(reference(beanManager, bean, ReadinessContributor.class));
        }
        return contributors;
    }

    /**
     * \u4ece CDI \u4e2d\u89e3\u6790\u6307\u5b9a\u7c7b\u578b\u7684\u5e94\u7528\u7ec4\u4ef6\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @param type \u8981\u89e3\u6790\u7684 Bean \u7c7b\u578b\u6216 CDI \u7c7b\u578b\u4fe1\u606f
     * @return \u89e3\u6790\u5230\u7684\u7ec4\u4ef6\u5b9e\u4f8b
     */
    private <T> T bean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        if (beans.isEmpty()) {
            return null;
        }
        Bean<?> bean = beanManager.resolve(beans);
        return reference(beanManager, bean, type);
    }

    /**
     * \u83b7\u53d6 CDI Bean \u5f15\u7528\uff0c\u5e76\u767b\u8bb0\u9000\u51fa\u65f6\u9700\u91ca\u653e\u7684 CreationalContext\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param beanManager \u7528\u6765\u53d1\u73b0\u6216\u83b7\u53d6 CDI Bean \u7684\u7ba1\u7406\u5668
     * @param bean \u88ab\u9009\u5b9a\u7528\u4e8e\u521b\u5efa\u5f15\u7528\u7684 CDI Bean \u5bf9\u8c61
     * @param type \u8981\u89e3\u6790\u7684 Bean \u7c7b\u578b\u6216 CDI \u7c7b\u578b\u4fe1\u606f
     * @return \u5f53\u524d\u90e8\u7f72\u53ef\u4f7f\u7528\u7684 CDI Bean \u5f15\u7528
     */
    private <T> T reference(BeanManager beanManager, Bean<?> bean, Class<T> type) {
        CreationalContext<?> context = beanManager.createCreationalContext(bean);
        return type.cast(beanManager.getReference(bean, type, context));
    }
}
