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
import io.ddd4j.sample.javalin.order.infrastructure.JavalinOrderAdapters;
import io.ddd4j.sample.javalin.order.web.OrderController;
import io.ddd4j.sample.javalin.spi.DefaultI18nProvider;
import io.ddd4j.sample.javalin.spi.NoOpDomainEventPublisher;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.jdbc.JdbcOrderSampleRuntime;
import io.ddd4j.web.javalin.Ddd4jJavalinWeb;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Javalin runtime wiring for the shared production-style Order sample.
 */
@Slf4j
public final class JavalinSample {

    private JavalinSample() {
    }

    /** @param args 启动参数 */
    public static void main(String[] args) {
        JavalinApplication application = startBasic(7000);
        log.info("Javalin Order memory demo started at http://localhost:{}", application.app().port());
    }

    /**
     * @param port HTTP 监听端口
     * @return 已启动的 Javalin 应用
     */
    public static JavalinApplication start(int port) {
        JavalinOrderAdapters adapters = new JavalinOrderAdapters();
        OrderApplicationService service = new OrderApplicationService(adapters, adapters, adapters, adapters);
        return start(port, service, null);
    }

    /**
     * 无认证、总线或后台资源的内存订单示例。
     * @param port HTTP 监听端口
     * @return 可关闭应用
     */
    public static JavalinApplication startBasic(int port) {
        JavalinOrderAdapters adapters = new JavalinOrderAdapters();
        return start(port, new OrderApplicationService(adapters, adapters, adapters, adapters), null, false);
    }

    /**
     * 使用已启动的 JDBC 订单运行实例提供 HTTP 服务；关闭返回句柄时一并停止 Outbox 工作器。
     * @param port HTTP 监听端口
     * @param runtime 已启动且所有权交给返回句柄的 JDBC 运行实例
     * @return 已启动的 Javalin 应用
     */
    public static JavalinApplication startJdbc(int port, JdbcOrderSampleRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime must not be null");
        return start(port, runtime.applicationService(), runtime);
    }

    private static JavalinApplication start(int port, OrderApplicationService service,
                                            JdbcOrderSampleRuntime jdbcRuntime) {
        return start(port, service, jdbcRuntime, true);
    }

    private static JavalinApplication start(int port, OrderApplicationService service,
                                            JdbcOrderSampleRuntime jdbcRuntime, boolean demoAuth) {
        DomainEventPublisher eventPublisher = new NoOpDomainEventPublisher();
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
            OrderController controller = new OrderController(service);
            app = Javalin.create(config -> {
                config.startup.showJavalinBanner = false;
                config.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build(), false));
                Ddd4jJavalinWeb web = demoAuth ? new Ddd4jJavalinWeb()
                        : new Ddd4jJavalinWeb(new WebRequestContextFactory(), new WebRequestLifecycle(
                                new BearerSubjectAuthenticator(), new PathWebAccessPolicy(java.util.List.of(),
                                AuthenticationMode.DISABLED)), new DefaultWebExceptionTranslator(), null);
                web.configure(config);
                config.routes.apiBuilder(controller::routes);
            });
            app.start(port);
            return new JavalinApplication(app, token, spiScope, jdbcRuntime);
        } catch (RuntimeException | Error failure) {
            try {
                stop(app, spiScope, jdbcRuntime);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static void stop(Javalin app, SpiRegistrationScope spiScope, JdbcOrderSampleRuntime jdbcRuntime) {
        try (ThreadContext.Scope callerContext = ThreadContext.open();
             SpiRegistrationScope registrations = spiScope;
             JdbcOrderSampleRuntime runtime = jdbcRuntime) {
            if (Objects.nonNull(app)) {
                app.stop();
            }
        }
    }

    /** 可关闭的 Javalin 示例运行实例。 */
    public final static class JavalinApplication implements AutoCloseable {

        private final Javalin app;

        private final String token;

        private final SpiRegistrationScope spiScope;

        private final JdbcOrderSampleRuntime jdbcRuntime;

        /**
         * @param app Javalin 服务实例
         * @param token 演示登录令牌
         * @param spiScope SPI 注册作用域
         */
        public JavalinApplication(Javalin app, String token, SpiRegistrationScope spiScope) {
            this(app, token, spiScope, null);
        }

        private JavalinApplication(Javalin app, String token, SpiRegistrationScope spiScope,
                                   JdbcOrderSampleRuntime jdbcRuntime) {
            Objects.requireNonNull(app, "app must not be null");
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(spiScope, "spiScope must not be null");
            this.app = app;
            this.token = token;
            this.spiScope = spiScope;
            this.jdbcRuntime = jdbcRuntime;
        }

        @Override
        public void close() {
            stop(app, spiScope, jdbcRuntime);
        }

        /** @return Javalin 服务实例 */
        public Javalin app() {
            return app;
        }

        /** @return 演示登录令牌 */
        public String token() {
            return token;
        }

        /** @return SPI 注册作用域 */
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
