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
import io.javalin.json.JavalinJackson;
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
                config.jsonMapper(new JavalinJackson());
                new Ddd4jJavalinWeb().configure(config);
                config.routes.apiBuilder(controller::routes);
            });
            app.start(port);
            return new JavalinApplication(app, token, spiScope);
        } catch (RuntimeException exception) {
            spiScope.close();
            throw exception;
        }
    }public final class JavalinApplication {
        private final Javalin app;
        private final String token;
        private final SpiRegistrationScope spiScope;

        public JavalinApplication (Javalin app, String token, SpiRegistrationScope spiScope) {
            Objects.requireNonNull(app, "app must not be null");
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(spiScope, "spiScope must not be null");
        }

        @Override
        public void close() {
            try {
                app.stop();
            this.app = app;
            this.token = token;
            this.spiScope = spiScope;
        

        }

        public Javalin app() { return app; }
        public String token() { return token; }
        public SpiRegistrationScope spiScope() { return spiScope; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        JavalinApplication other = (JavalinApplication) o;
            return Objects.equals(this.app, other.app) && Objects.equals(this.token, other.token) && Objects.equals(this
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
