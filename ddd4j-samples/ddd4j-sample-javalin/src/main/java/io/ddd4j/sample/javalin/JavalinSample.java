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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList;
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
import io.javalin.plugin.json.JavalinJackson;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Javalin runtime wiring for the shared production-style Order sample.
 */
@Slf4j
public final class JavalinSample {

    private JavalinSample() {
    }

    /**
     * @param args 启动参数
     */
    public static void main(String[] args) {
        JavalinApplication application = startBasic(Integer.getInteger("sample.port", 7000));
        log.info("Javalin catalog trade started at http://localhost:{}", application.app().port());
    }

    /**
     * @param port HTTP 监听端口
     * @return 已启动的 Javalin 应用
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
                    .setPerms(ImmutableSet.of("order:read", "order:write"))));
            spiScope.register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, new InMemorySubjectProvider(subject));
        }
        Javalin app = null;
        try {
            spiScope.start();
            app = Javalin.create(config -> {
                config.showJavalinBanner = false;
                config.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build()));
            });
            Ddd4jJavalinWeb web = demoAuth ? new Ddd4jJavalinWeb()
                        : new Ddd4jJavalinWeb(new WebRequestContextFactory(), new WebRequestLifecycle(
                        new BearerSubjectAuthenticator(), new PathWebAccessPolicy(ImmutableList.of(),
                        AuthenticationMode.DISABLED)), new DefaultWebExceptionTranslator(), null);
            web.configure(app);
            app.routes(() -> CatalogTradeRoutes.register(catalog));
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

        private final Javalin app;

        private final String token;

        private final SpiRegistrationScope spiScope;

        /**
         * @param app      Javalin 服务实例
         * @param token    演示登录令牌
         * @param spiScope SPI 注册作用域
         */
        public JavalinApplication(Javalin app, String token, SpiRegistrationScope spiScope) {
            Objects.requireNonNull(app, "app must not be null");
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(spiScope, "spiScope must not be null");
            this.app = app;
            this.token = token;
            this.spiScope = spiScope;
        }

        @Override
        public void close() {
            stop(app, spiScope);
        }

        /**
         * @return Javalin 服务实例
         */
        public Javalin app() {
            return app;
        }

        /**
         * @return 演示登录令牌
         */
        public String token() {
            return token;
        }

        /**
         * @return SPI 注册作用域
         */
        public SpiRegistrationScope spiScope() {
            return spiScope;
        }

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

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(app);
            result = 31 * result + Objects.hashCode(token);
            result = 31 * result + Objects.hashCode(spiScope);
            return result;
        }

        @Override
        public String toString() {
            return "JavalinApplication[app=" + app + ", token=[REDACTED], spiScope=" + spiScope + "]";
        }
    }
}
