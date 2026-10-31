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

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.SpiRegistrationScope;
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
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson3;
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
     * 已启动的 Javalin 应用句柄：持有 HTTP 实例、演示用 Bearer 令牌与 SPI 注册作用域。
     *
     * <p>实现 {@link AutoCloseable}，关闭时先停止 HTTP 服务，再关闭 SPI 作用域。
     *
     * 字段 {@code app}：已启动的 Javalin HTTP 实例
     * 字段 {@code token}：演示登录后返回的 Bearer 令牌
     * 字段 {@code spiScope}：本次启动注册的 SPI 作用域
     */
    public final static class JavalinApplication implements AutoCloseable {

        private final Javalin app;

        private final String token;

        private final SpiRegistrationScope spiScope;

        private final JdbcOrderSampleRuntime jdbcRuntime;

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

        /**
         * 关闭并释放资源。
         */
        @Override
        public void close() {
            try {
                app.stop();
            } finally {
                try {
                    if (Objects.nonNull(jdbcRuntime)) {
                        jdbcRuntime.close();
                    }
                } finally {
                    spiScope.close();
                }
            }
        }

        public Javalin app() {
            return app;
        }

        public String token() {
            return token;
        }

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

    /**
     * main() 方法。
     * @param args 命令行参数。
     */
    public static void main(String[] args) {
        JavalinApplication application = start(7000);
        /**
         * log.info() 方法。
         * @param {}" {}" 参数。
         * @param application.app().port() application.app().port() 参数。
         * @param application.token() application.token() 参数。
         */
        log.info("Javalin Order sample started at http://localhost:{}; demo Bearer token: {}",
                application.app().port(), application.token());
    }

    /**
     * 启动。
     * @param port port 参数。
     * @return 方法执行结果。
     */
    public static JavalinApplication start(int port) {
        JavalinOrderAdapters adapters = new JavalinOrderAdapters();
        OrderApplicationService service = new OrderApplicationService(adapters, adapters, adapters, adapters);
        return start(port, service, null);
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
        DomainEventPublisher eventPublisher = new NoOpDomainEventPublisher();
        InMemorySubject subject = new InMemorySubject(event -> log.debug("Authentication event: {}", event));
        String token = subject.login(AuthRequest.of("sample-user").setPrincipal(new AuthPrincipal()
                .setLoginId("sample-user")
                .setUserId("sample-user")
                .setPerms(java.util.Set.of("order:read", "order:write"))));

        SpiRegistrationScope spiScope = new SpiRegistrationScope()
                .register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, eventPublisher)
                .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, new InMemorySubjectProvider(subject))
                .register(SpiKeys.I18N_PROVIDER, I18nProvider.class, new DefaultI18nProvider());
        try {
            spiScope.start();
            OrderController controller = new OrderController(service);
            Javalin app = Javalin.create(config -> {
                config.startup.showJavalinBanner = false;
                config.jsonMapper(new JavalinJackson3());
                new Ddd4jJavalinWeb().configure(config);
                config.routes.apiBuilder(controller::routes);
            });
            app.start(port);
            return new JavalinApplication(app, token, spiScope, jdbcRuntime);
        } catch (RuntimeException exception) {
            try {
                if (Objects.nonNull(jdbcRuntime)) {
                    jdbcRuntime.close();
                }
            } finally {
                spiScope.close();
            }
            throw exception;
        }
    }
}
