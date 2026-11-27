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
package io.ddd4j.sample.javalin;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.javalin.spi.DefaultI18nProvider;
import com.google.common.eventbus.EventBus;
import io.ddd4j.guice.event.GuiceDomainEventPublisher;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.javalin.Ddd4jJavalinWeb;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Javalin runtime wiring for the shared production-style Order sample.
 */
@Slf4j
public final class JavalinSample {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>JavalinSample</code> \u5b9e\u4f8b\u3002 */
    private JavalinSample() {
    }

    /** @param args \u542f\u52a8\u53c2\u6570 */
    public static void main(String[] args) {
        JavalinApplication application = startBasic(Integer.getInteger("sample.port", 7000));
        log.info("Javalin catalog trade started at http://localhost:{}", application.app().port());
    }

    /**
     * 执行 <code>start</code>，调用 <code>CatalogRuntime.start</code>。
     *
     * @param port HTTP \u76d1\u542c\u7aef\u53e3
     * @return \u5df2\u542f\u52a8\u7684 Javalin \u5e94\u7528
     */
    public static JavalinApplication start(int port) {
        return start(port, CatalogRuntime.start(), true);
    }

    /**
     * 启动匿名商品交易示例。
     *
     * @param port HTTP 监听端口
     * @return 可关闭应用
     */
    public static JavalinApplication startBasic(int port) {
        return start(port, CatalogRuntime.start(), false);
    }

    /**
     * \u88c5\u914d\u5546\u54c1\u4ea4\u6613\u8def\u7531\u5e76\u542f\u52a8 Javalin\uff0c\u53ef\u542f\u7528\u5185\u5b58\u8ba4\u8bc1\u6f14\u793a\u3002
     * @param port \u76d1\u542c\u7aef\u53e3
     * @param catalog \u5df2\u88c5\u914d\u7684\u5546\u54c1\u4ea4\u6613\u64cd\u4f5c
     * @param demoAuth \u662f\u5426\u542f\u7528\u5185\u5b58\u8ba4\u8bc1\u6f14\u793a
     * @return \u6301\u6709 HTTP \u670d\u52a1\u548c\u8fd0\u884c\u8d44\u6e90\u7684\u5e94\u7528\u5b9e\u4f8b
     */
    public static JavalinApplication start(int port, CatalogEndpoints catalog, boolean demoAuth) {
        DomainEventPublisher eventPublisher = new GuiceDomainEventPublisher(new EventBus("catalog-trade"));
        String token = "";

        SpiRegistrationScope spiScope = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, eventPublisher)
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class, new DefaultI18nProvider());
        if (demoAuth) {
            InMemorySubject subject = new InMemorySubject(event -> log.debug("Authentication event: {}", event));
            token = subject.login(AuthRequest.of("sample-user").setPrincipal(new AuthPrincipal()
                    .setLoginId("sample-user").setUserId("sample-user")
                    .setPerms(java.util.Set.of("order:read", "order:write"))));
            spiScope.register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, new InMemorySubjectProvider(subject));
        }
        Javalin app = null;
        try {
            spiScope.start();
            app = Javalin.create(config -> {
                config.startup.showJavalinBanner = false;
                config.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build(), false));
                Ddd4jJavalinWeb web = demoAuth ? new Ddd4jJavalinWeb()
                        : new Ddd4jJavalinWeb(new WebRequestContextFactory(), new WebRequestLifecycle(
                        new BearerSubjectAuthenticator(), new PathWebAccessPolicy(java.util.List.of(),
                        AuthenticationMode.DISABLED)), new DefaultWebExceptionTranslator(), null);
                web.configure(config);
                config.routes.apiBuilder(() -> CatalogTradeRoutes.register(catalog));
            });
            app.start(port);
            return new JavalinApplication(app, token, spiScope);
        } catch (RuntimeException | Error failure) {
            try {
                stop(app, spiScope);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * \u505c\u6b62 Javalin \u5e94\u7528\u5e76\u91ca\u653e\u5f53\u524d\u793a\u4f8b\u7684\u7ec4\u4ef6\u6ce8\u518c\u3002
     * @param app \u5f53\u524d\u62e5\u6709\u8def\u7531\u7684 Javalin \u5e94\u7528
     * @param spiScope \u6301\u6709\u5e94\u7528\u6240\u6ce8\u518c\u5e95\u5c42\u7ec4\u4ef6\u7684\u4f5c\u7528\u57df
     */
    private static void stop(Javalin app, SpiRegistrationScope spiScope) {
        try (ThreadContext.Scope callerContext = ThreadContext.open();
             SpiRegistrationScope registrations = spiScope) {
            if (Objects.nonNull(app)) {
                app.stop();
            }
        }
    }

    /**
     * 可关闭的 Javalin 示例运行实例。
     */
    public final static class JavalinApplication implements AutoCloseable {

        /** \u5f53\u524d\u793a\u4f8b\u62e5\u6709\u7684 Javalin \u5e94\u7528\u3002 */
        private final Javalin app;

        /** \u5f53\u524d\u793a\u4f8b\u7b7e\u53d1\u6216\u8fd4\u56de\u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002 */
        private final String token;

        /** \u7ba1\u7406\u5f53\u524d\u793a\u4f8b\u6216\u6d4b\u8bd5\u6240\u62e5\u6709\u7684 SPI \u6ce8\u518c\u8d44\u6e90\u3002 */
        private final SpiRegistrationScope spiScope;

        /**
         * 初始化 <code>JavalinApplication</code> 使用的 <code>app</code>、<code>token</code>、<code>spiScope</code>。
         *
         * @param app Javalin \u670d\u52a1\u5b9e\u4f8b
         * @param token \u6f14\u793a\u767b\u5f55\u4ee4\u724c
         * @param spiScope SPI \u6ce8\u518c\u4f5c\u7528\u57df
         */
        public JavalinApplication(Javalin app, String token, SpiRegistrationScope spiScope) {
            Objects.requireNonNull(app, "app must not be null");
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(spiScope, "spiScope must not be null");
            this.app = app;
            this.token = token;
            this.spiScope = spiScope;
        }

        /**
         * 结束当前组件持有的资源。
         *
         * <p>Javalin 实现调用 <code>stop(app, spiScope)</code>。</p>
         */
        @Override
        public void close() {
            stop(app, spiScope);
        }

        /** @return Javalin \u670d\u52a1\u5b9e\u4f8b */
        public Javalin app() {
            return app;
        }

        /** @return \u6f14\u793a\u767b\u5f55\u4ee4\u724c */
        public String token() {
            return token;
        }

        /** @return SPI \u6ce8\u518c\u4f5c\u7528\u57df */
        public SpiRegistrationScope spiScope() {
            return spiScope;
        }

        /**
         * 根据<code>app</code>、<code>token</code>、<code>spiScope</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            JavalinApplication other = (JavalinApplication) obj;
            return Objects.equals(this.app, other.app) && Objects.equals(this.token, other.token) && Objects.equals(this.spiScope, other.spiScope);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(app);
            result = 31 * result + Objects.hashCode(token);
            result = 31 * result + Objects.hashCode(spiScope);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>app</code>、<code>token</code>、<code>spiScope</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "JavalinApplication[app=" + app + ", token=[REDACTED], spiScope=" + spiScope + "]";
        }
    }
}
