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
import io.ddd4j.web.javalin.Ddd4jJavalinWeb;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson3;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/** Javalin runtime wiring for the shared production-style Order sample. */
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
                config.jsonMapper(new JavalinJackson3());
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

    public record JavalinApplication(Javalin app, String token, SpiRegistrationScope spiScope)
            implements AutoCloseable {

        public JavalinApplication {
            Objects.requireNonNull(app, "app must not be null");
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(spiScope, "spiScope must not be null");
        }

        @Override
        public void close() {
            try {
                app.stop();
            } finally {
                spiScope.close();
            }
        }
    }
}
