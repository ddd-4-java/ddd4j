package io.ddd4j.web.vertx;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Vert.x Router 的标准 ddd4j handler 链。
 */
@Slf4j
public final class Ddd4jVertxWeb {

    private static final String SCOPE_KEY = Ddd4jVertxWeb.class.getName() + ".scope";

    private final BearerSubjectAuthenticator authenticator;
    private final WebExceptionTranslator translator;
    private final Predicate<String> publicPath;

    public Ddd4jVertxWeb() {
        this(new BearerSubjectAuthenticator(), new DefaultWebExceptionTranslator(), Ddd4jVertxWeb::isPublicPath);
    }

    public Ddd4jVertxWeb(BearerSubjectAuthenticator authenticator, WebExceptionTranslator translator,
                         Predicate<String> publicPath) {
        this.authenticator = authenticator;
        this.translator = translator;
        this.publicPath = publicPath;
    }

    public void install(Router router) {
        Objects.requireNonNull(router, "router must not be null");
        router.route().handler(contextHandler());
        router.route().handler(authenticationHandler());
        router.route().failureHandler(failureHandler());
    }

    public Handler<RoutingContext> contextHandler() {
        return routingContext -> {
            String requestId = headerOrGenerated(routingContext, WebHeaders.REQUEST_ID);
            WebContextScope scope = WebContextScope.open(toContext(routingContext, requestId));
            routingContext.put(SCOPE_KEY, scope);
            routingContext.response().putHeader(WebHeaders.REQUEST_ID, requestId);
            routingContext.addEndHandler(ignored -> closeScope(routingContext));
            routingContext.next();
        };
    }

    public Handler<RoutingContext> authenticationHandler() {
        return routingContext -> {
            try {
                if (!publicPath.test(routingContext.normalizedPath())) {
                    ThreadContext.bind(authenticator.authenticateSubject(
                            routingContext.request().getHeader(WebHeaders.AUTHORIZATION)).subject());
                }
                routingContext.next();
            } catch (RuntimeException exception) {
                routingContext.fail(exception);
            }
        };
    }

    public Handler<RoutingContext> failureHandler() {
        return routingContext -> {
            Throwable failure = Objects.nonNull(routingContext.failure())
                    ? routingContext.failure()
                    : new IllegalStateException("HTTP request failed with status " + routingContext.statusCode());
            WebError error = translator.translate(failure);
            if (error.status() >= 500) {
                log.error("Unhandled HTTP request failure: {} {}", routingContext.request().method(),
                        routingContext.normalizedPath(), failure);
            }
            if (!routingContext.response().ended()) {
                routingContext.response().setStatusCode(error.status())
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .end(JsonKit.toJson(error.toResponse()));
            }
            closeScope(routingContext);
        };
    }

    private WebRequestContext toContext(RoutingContext context, String requestId) {
        String forwardedFor = context.request().getHeader(WebHeaders.FORWARDED_FOR);
        String clientIp = StrKit.isBlank(forwardedFor)
                ? context.request().remoteAddress().host()
                : forwardedFor.split(",", 2)[0].trim();
        Locale locale = Objects.isNull(context.preferredLanguage())
                ? Locale.getDefault()
                : Locale.forLanguageTag(context.preferredLanguage().tag());
        return new WebRequestContext(requestId, context.request().getHeader(WebHeaders.TRACE_ID),
                context.request().getHeader(WebHeaders.TENANT_ID), context.request().getHeader(WebHeaders.AUTHORIZATION),
                locale, clientIp, context.request().method().name(), context.normalizedPath());
    }

    private String headerOrGenerated(RoutingContext context, String header) {
        String value = context.request().getHeader(header);
        return StrKit.isBlank(value) ? UUID.randomUUID().toString() : value;
    }

    private void closeScope(RoutingContext context) {
        WebContextScope scope = context.get(SCOPE_KEY);
        if (Objects.nonNull(scope)) {
            scope.close();
            context.remove(SCOPE_KEY);
        }
    }

    private static boolean isPublicPath(String path) {
        return "/health".equals(path) || "/health/readiness".equals(path) || "/health/liveness".equals(path);
    }
}
