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
package io.ddd4j.guice.core;

import com.google.common.eventbus.EventBus;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.ddd4j.core.contract.DomainEvent;
import io.ddd4j.core.contract.DomainEventPublisher;
import io.ddd4j.core.context.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.context.GuiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ddd4j Google Guice 核心模块。
 * <p>
 * 注册 3 个核心 SPI 的 Guice 实现：
 * <ul>
 *   <li>{@link DomainEventPublisher} → {@link GuiceDomainEventPublisher}（基于 Guava EventBus）</li>
 *   <li>{@link SubjectProvider} → {@link GuiceSubjectProvider}（基于 Guice Injector）</li>
 *   <li>{@link I18nProvider} → {@link GuiceI18nProvider}（基于 ResourceBundle）</li>
 * </ul>
 * <p>
 * 用户在自己的 Guice Injector 中 install(this) 即可启用 ddd4j 全部功能：
 * <pre>{@code
 * Injector injector = Guice.createInjector(new Ddd4jGuiceModule());
 * DomainEventPublisher publisher = injector.getInstance(DomainEventPublisher.class);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public class Ddd4jGuiceModule extends AbstractModule {

    private static final Logger LOG = LoggerFactory.getLogger(Ddd4jGuiceModule.class);

    @Override
    protected void configure() {
        // 注册 GuiceContext 初始化
        bind(GuiceContextInitializer.class).asEagerSingleton();
        // 注册 3 个核心 SPI
        bind(DomainEventPublisher.class).to(GuiceDomainEventPublisher.class).in(Singleton.class);
        bind(SubjectProvider.class).to(GuiceSubjectProvider.class).in(Singleton.class);
        bind(I18nProvider.class).to(GuiceI18nProvider.class).in(Singleton.class);
    }

    /**
     * 提供 Guava EventBus（单例）
     */
    @Provides
    @Singleton
    public EventBus eventBus() {
        EventBus bus = new EventBus();
        LOG.info("Guava EventBus created");
        return bus;
    }

    /**
     * GuiceContext 初始化器（Eager Singleton）
     */
    public static class GuiceContextInitializer {
        @com.google.inject.Inject
        public GuiceContextInitializer(Injector injector) {
            GuiceContext.setInjector(injector);
            // 注册 DomainEventPublisher 到 DomainEvent 静态字段
            DomainEventPublisher publisher = injector.getInstance(DomainEventPublisher.class);
            DomainEvent.registerPublisher(publisher);
            LOG.info("GuiceContext and DomainEventPublisher initialized");
        }
    }
}
