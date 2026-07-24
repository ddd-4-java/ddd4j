package io.ddd4j.web.vertx;

import io.ddd4j.web.core.AuthenticationMode;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.PathWebAccessPolicy;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebOtelSupport;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestData;
import io.ddd4j.web.core.WebRequestLifecycle;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Vert.x Router 的标准请求上下文、认证、幂等和异常处理链。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Slf4j
public final class Ddd4jVertxWeb {

    private static final String STATE_KEY = Ddd4jVertxWeb.class.getName() + ".state";
    private static final String OTEL_SPAN_KEY = Ddd4jVertxWeb.class.getName() + ".otelSpan";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final WebExceptionTranslator exceptionTranslator;
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    private final Function<Object, String> jsonEncoder;

    public Ddd4jVertxWeb() {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness"),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, Json::encode);
    }

    public Ddd4jVertxWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                         WebExceptionTranslator exceptionTranslator,
                         WebIdempotencyLifecycle idempotencyLifecycle,
                         Function<Object, String> jsonEncoder) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
        this.jsonEncoder = Objects.requireNonNull(jsonEncoder, "jsonEncoder must not be null");
    }

    public void install(Router router) {
        Router target = Objects.requireNonNull(router, "router must not be null");
        target.route().handler(contextHandler());
        target.route().handler(authenticationHandler());
        target.route().failureHandler(failureHandler());
    }

    public Handler<RoutingContext> contextHandler() {
        return routingContext -> {
            // OTel: 提取上游 TraceContext 并开启 SERVER span
            Map<String, String> headers = extractHeaders(routingContext);
            Object span = WebOtelSupport.startServerSpan(
                    routingContext.request().method().name(),
                    routingContext.normalizedPath(),
                    headers);
            routingContext.put(OTEL_SPAN_KEY, span);
            WebOtelSupport.activate(span);

            WebRequestContext requestContext = createContext(routingContext);
            routingContext.put(STATE_KEY, new RequestState());
            Ddd4jVertxContext.bindRequest(routingContext, requestContext);
            routingContext.response().putHeader(WebHeaders.REQUEST_ID, requestContext.requestId());
            routingContext.response().putHeader(WebHeaders.TRACE_ID, requestContext.traceId());
            routingContext.addEndHandler(ignored -> finish(routingContext));
            routingContext.next();
        };
    }

    public Handler<RoutingContext> authenticationHandler() {
        return routingContext -> {
            WebRequestContext requestContext = Ddd4jVertxContext.request(routingContext).orElseThrow();
            routingContext.vertx().executeBlocking(() -> authenticate(routingContext, requestContext))
                    .onSuccess(result -> {
                        result.authentication().ifPresent(authentication ->
                                Ddd4jVertxContext.bindSubject(routingContext, authentication.subject()));
                        RequestState state = routingContext.get(STATE_KEY);
                        result.idempotencyScope().ifPresent(state::idempotencyScope);
                        routingContext.next();
                    })
                    .onFailure(routingContext::fail);
        };
    }

    public Handler<RoutingContext> failureHandler() {
        return routingContext -> {
            Throwable failure = Objects.nonNull(routingContext.failure())
                    ? routingContext.failure()
                    : new IllegalStateException("HTTP request failed with status " + routingContext.statusCode());
            RequestState state = routingContext.get(STATE_KEY);
            if (Objects.nonNull(state)) {
                state.failed();
            }
            WebError error = exceptionTranslator.translate(failure);
            if (error.status() >= 500) {
                log.error("Unhandled Vert.x request failure: {} {}", routingContext.request().method(),
                        routingContext.normalizedPath(), failure);
            }
            // OTel: 记录异常
            Object span = routingContext.get(OTEL_SPAN_KEY);
            if (span != null) {
                WebOtelSupport.recordError(span, failure);
                WebOtelSupport.endServerSpan(span, error.status());
            }
            if (!routingContext.response().ended()) {
                routingContext.response().setStatusCode(error.status())
                        .putHeader("Content-Type", "application/json")
                        .end(jsonEncoder.apply(error.toResponse()));
            }
        };
    }

    private static Map<String, String> extractHeaders(RoutingContext context) {
        Map<String, String> headers = new HashMap<>();
        context.request().headers().forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && value != null) {
                headers.put(key, value);
            }
        });
        return headers;
    }

    private AuthenticationResult authenticate(RoutingContext routingContext, WebRequestContext requestContext) {
        Optional<BearerSubjectAuthenticator.Authentication> authentication = requestLifecycle
                .authenticate(requestContext);
        Optional<WebIdempotencyLifecycle.Scope> idempotencyScope = idempotencyLifecycle.flatMap(lifecycle ->
                lifecycle.open(requestContext,
                        routingContext.request().getHeader(WebHeaders.IDEMPOTENCY_KEY)));
        return new AuthenticationResult(authentication, idempotencyScope);
    }

    private WebRequestContext createContext(RoutingContext context) {
        Locale locale = Objects.isNull(context.preferredLanguage())
                ? Locale.getDefault() : Locale.forLanguageTag(context.preferredLanguage().tag());
        return contextFactory.create(new WebRequestData(
                context.request().getHeader(WebHeaders.REQUEST_ID),
                context.request().getHeader(WebHeaders.TRACE_ID),
                context.request().getHeader(WebHeaders.TENANT_ID),
                context.request().getHeader(WebHeaders.AUTHORIZATION),
                locale,
                context.request().getHeader(WebHeaders.FORWARDED_FOR),
                context.request().getHeader("X-Real-IP"),
                context.request().remoteAddress().host(),
                context.request().method().name(),
                context.normalizedPath()));
    }

    private void finish(RoutingContext context) {
        RequestState state = context.remove(STATE_KEY);
        if (Objects.isNull(state)) {
            return;
        }
        boolean successful = !state.failed && context.response().getStatusCode() < 400;
        context.vertx().executeBlocking(() -> {
            state.close(successful);
            return null;
        }).onFailure(exception -> log.error("Unable to close Vert.x request state", exception));

        // OTel: 结束 span
        Object span = context.remove(OTEL_SPAN_KEY);
        if (span != null) {
            int status = context.response().getStatusCode() > 0
                    ? context.response().getStatusCode() : 200;
            if (state.failed) {
                WebOtelSupport.recordError(span, new RuntimeException("request failed"));
            }
            WebOtelSupport.endServerSpan(span, status);
        }
    }

    private record AuthenticationResult(
            Optional<BearerSubjectAuthenticator.Authentication> authentication,
            Optional<WebIdempotencyLifecycle.Scope> idempotencyScope) {
    }

    private static final class RequestState {

        private WebIdempotencyLifecycle.Scope idempotencyScope;
        private boolean failed;

        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        private void failed() {
            failed = true;
        }

        private void close(boolean successful) {
            if (Objects.isNull(idempotencyScope)) {
                return;
            }
            if (successful) {
                idempotencyScope.complete();
            }
            idempotencyScope.close();
        }
    }
}