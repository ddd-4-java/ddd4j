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
package io.ddd4j.guice;

import com.google.common.eventbus.EventBus;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.guice.cqrs.GuiceViewManager;
import io.ddd4j.guice.event.GuiceDomainEventPublisher;
import io.ddd4j.guice.i18n.GuiceI18nProvider;
import io.ddd4j.guice.subject.GuiceSubjectProvider;
import lombok.extern.slf4j.Slf4j;

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
 * @since 2.0.x
 */
@Slf4j
public class Ddd4jGuiceModule extends AbstractModule {

    @Override
    protected void configure() {
        // 注册 GuiceContext 初始化
        bind(GuiceContextInitializer.class).asEagerSingleton();
        // 注册 3 个核心 SPI
        bind(DomainEventPublisher.class).to(GuiceDomainEventPublisher.class).in(Singleton.class);
        bind(SubjectProvider.class).to(GuiceSubjectProvider.class).in(Singleton.class);
        bind(I18nProvider.class).to(GuiceI18nProvider.class).in(Singleton.class);
        bind(ProjectionPositionRepository.class).to(InMemoryProjectionPositionRepository.class).in(Singleton.class);
        bind(ProjectionService.class).to(DefaultProjectionService.class).in(Singleton.class);
        bind(GuiceViewManager.class).in(Singleton.class);
        bind(ViewManager.class).to(GuiceViewManager.class);
        bind(ViewScheduler.class).to(GuiceViewManager.class);
    }

    /**
     * 提供 Guava EventBus（单例）
     */
    @Provides
    @Singleton
    public EventBus eventBus() {
        EventBus bus = new EventBus();
        log.info("Guava EventBus created");
        return bus;
    }

    /**
     * 默认投影运行器。业务侧可在自己的模块中绑定更具体的事件读取器和运行器。
     */
    @Provides
    @Singleton
    public ProjectionRunner<Object> projectionRunner(ProjectionService projectionService) {
        return new ProjectionRunner<>(projectionService, new NoopEventChunkReader<>());
    }

    /**
     * GuiceContext 初始化器（Eager Singleton）
     * <p>
     * 启动期将 Guice 管理的 SPI 实现注入到 ddd4j 上下文（替代旧的 DomainEvent.registerPublisher 静态注册）：
     * <ul>
     *   <li>{@link DomainEventPublisher} → {@link SpiKeys#DOMAIN_EVENT_PUBLISHER}</li>
     *   <li>{@link SubjectProvider} → {@link SpiKeys#SUBJECT_PROVIDER}</li>
     *   <li>{@link I18nProvider} → {@link SpiKeys#I18N_PROVIDER}</li>
     * </ul>
     */
    public static class GuiceContextInitializer {
        @com.google.inject.Inject
        public GuiceContextInitializer(Injector injector) {
            GuiceContext.setInjector(injector);

            // 注册 4 个核心 SPI 到上下文（线程级优先 → 全局兜底）
            BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                    injector.getInstance(DomainEventPublisher.class));
            BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                    injector.getInstance(SubjectProvider.class));
            BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class,
                    injector.getInstance(I18nProvider.class));

            log.info("GuiceContext and ddd4j SPI services initialized");
        }
    }
}
