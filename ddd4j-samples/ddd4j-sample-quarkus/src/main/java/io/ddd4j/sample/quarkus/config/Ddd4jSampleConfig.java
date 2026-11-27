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
package io.ddd4j.sample.quarkus.config;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.quarkus.runtime.StartupEvent;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * ddd4j Quarkus 示例启动配置 Bean。
 *
 * <p>虽然 ddd4j-runtime-quarkus 已经提供了
 * {@link io.ddd4j.quarkus.context.DddContextInitializer} 自动注入核心 SPI，
 * 本配置类演示：
 * <ol>
 *   <li>如何在 Quarkus 启动期拿到 SPI Bean 引用（用于运行时校验与日志）</li>
 *   <li>如何利用 {@link io.quarkus.runtime.StartupEvent} 监听应用启动完成事件</li>
 *   <li>如何利用 {@link ApplicationScoped} Bean 暴露给其他业务 Bean 注入</li>
 * </ol>
 *
 * <h2>运行效果</h2>
 * <p>应用启动后日志中会输出类似：
 * <pre>
 * [Ddd4jSampleConfig] DomainEventPublisher = QuarkusDomainEventPublisher
 * [Ddd4jSampleConfig] SubjectProvider        = SampleSubjectProvider
 * [Ddd4jSampleConfig] I18nProvider           = DefaultI18nProvider
 * </pre>
 *
 * <h2>扩展建议</h2>
 * <p>真实项目可在本类中加入：
 * <ul>
 *   <li>Quarkus 配置项绑定（{@code @ConfigProperty}）</li>
 *   <li>Redis / Kafka 客户端初始化</li>
 *   <li>多租户上下文预热</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class Ddd4jSampleConfig {

    /**
     * 核心 SPI Bean 引用（启动期注入）
     */
    @Inject
    Instance<DomainEventPublisher> domainEventPublisher;
    /** \u6839\u636e\u5f53\u524d\u8c03\u7528\u8303\u56f4\u63d0\u4f9b\u8ba4\u8bc1\u4e3b\u4f53\u7684\u7ec4\u4ef6\u3002 */
    @Inject
    Instance<SubjectProvider> subjectProvider;
    /** CDI \u73af\u5883\u4e2d\u53ef\u4f9b\u793a\u4f8b\u4e1a\u52a1\u4f7f\u7528\u7684\u56fd\u9645\u5316\u63d0\u4f9b\u5668\u3002 */
    @Inject
    Instance<I18nProvider> i18nProvider;

    /**
     * Quarkus 启动完成后回调：校验核心 SPI 是否成功注入，并打印日志。
     *
     * <p>{@code StartupEvent} 是 Quarkus 的标准启动信号，
     * ddd4j-runtime-quarkus 的 {@code DddContextInitializer} 也订阅同一事件，
     * 因此本方法与它的执行顺序不固定，业务方应避免依赖顺序。
     *
     * @param event 启动事件
     */
    void onStart(@Observes StartupEvent event) {
        CacheKit.build("ddd4j-web-idempotency",
                builder -> builder.maximumSize(10000).expireAfterWriteSeconds(300));
        log.info("[Ddd4jSampleConfig] Quarkus started, verifying ddd4j SPI services...");

        logSPI("DomainEventPublisher", domainEventPublisher);
        logSPI("SubjectProvider", subjectProvider);
        logSPI("I18nProvider", i18nProvider);

        log.info("[Ddd4jSampleConfig] Sample application is ready. Try GET /api/orders/{orderNo}");
    }

    /**
     * 供其他业务 Bean 注入使用的便捷方法。
     *
     * @return 进程内领域事件发布者
     */
    public Optional<DomainEventPublisher> domainEventPublisher() {
        return domainEventPublisher.isUnsatisfied() ? Optional.empty() : Optional.of(domainEventPublisher.get());
    }

    /**
     * \u8bb0\u5f55 CDI \u4e2d\u5b9e\u9645\u53d1\u73b0\u7684 SPI \u670d\u52a1\u5b9e\u73b0\u3002
     * @param <T> \u5f53\u524d\u793a\u4f8b\u6240\u4f7f\u7528\u7684\u6570\u636e\u6216\u6846\u67b6\u7c7b\u578b
     * @param name \u5f85\u8bfb\u53d6\u7684\u53c2\u6570\u3001Cookie\u3001\u8bf7\u6c42\u5934\u6216\u5176\u4ed6\u914d\u7f6e\u9879\u540d\u79f0
     * @param instance \u9700\u8981\u8bb0\u5f55\u53d1\u73b0\u7ed3\u679c\u7684 CDI \u7ec4\u4ef6\u5b9e\u4f8b
     */
    private <T> void logSPI(String name, Instance<T> instance) {
        if (instance.isUnsatisfied()) {
            log.warn("[Ddd4jSampleConfig] {} NOT FOUND (unsatisfied)", name);
            return;
        }
        if (instance.isAmbiguous()) {
            log.warn("[Ddd4jSampleConfig] {} AMBIGUOUS (multiple beans)", name);
            return;
        }
        T bean = instance.get();
        log.info("[Ddd4jSampleConfig] {} = {}", name, bean.getClass().getSimpleName());
    }
}
