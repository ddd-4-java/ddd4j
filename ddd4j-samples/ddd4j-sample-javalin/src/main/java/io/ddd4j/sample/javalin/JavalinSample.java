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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.InvalidObjectException;

import java.io.ObjectStreamException;

import com.fasterxml.jackson.databind.json.JsonMapper;
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

    private JavalinSample() {
    }

    public static void main(String[] args) {
        JavalinApplication application = start(7000);
        log.info("Javalin Order sample started at http://localhost:{}; demo Bearer token: {}",
                application.app().port(), application.token());
    }

    public static JavalinApplication start(int port) {
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
        spiScope.start();

        try {
            JavalinOrderAdapters adapters = new JavalinOrderAdapters();
            OrderApplicationService service = new OrderApplicationService(adapters, adapters, adapters, adapters);
            OrderController controller = new OrderController(service);
            Javalin app = Javalin.create(config -> {
                config.startup.showJavalinBanner = false;
                config.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build(), false));
                new Ddd4jJavalinWeb().configure(config);
                config.routes.apiBuilder(controller::routes);
            });
            app.start(port);
            return new JavalinApplication(app, token, spiScope);
        } catch (RuntimeException exception) {
            spiScope.close();
            throw exception;
        }
    }

    public final static class JavalinApplication implements AutoCloseable {

        private static final long serialVersionUID = 0L;

        private final Javalin app;

        private final String token;

        private final SpiRegistrationScope spiScope;

        @JsonCreator()
        public JavalinApplication(@JsonProperty("app") Javalin app, @JsonProperty("token") String token, @JsonProperty("spiScope") SpiRegistrationScope spiScope) {
            Objects.requireNonNull(app, "app must not be null");
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(spiScope, "spiScope must not be null");
            this.app = app;
            this.token = token;
            this.spiScope = spiScope;
        }

        @Override
        public void close() {
            try {
                app.stop();
            } finally {
                spiScope.close();
            }
        }

        @JsonProperty("app")
        public Javalin app() {
            return app;
        }

        @JsonProperty("token")
        public String token() {
            return token;
        }

        @JsonProperty("spiScope")
        public SpiRegistrationScope spiScope() {
            return spiScope;
        }

        private Object readResolve() throws ObjectStreamException {
            try {
                return new JavalinApplication(app, token, spiScope);
            } catch (RuntimeException cause) {
                InvalidObjectException failure = new InvalidObjectException(cause.getMessage());
                failure.initCause(cause);
                throw failure;
            }
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
            return "JavalinApplication[app=" + app + ", token=" + token + ", spiScope=" + spiScope + "]";
        }
    }
}
