package io.ddd4j.web.quarkus;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.RequestIdGenerator;
import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Quarkus REST 同步请求上下文、Bearer Subject 与幂等过滤器。
 *
 * <p>同时集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用
 * WebOtelIntegration（OTel 集成可选，无依赖时不生效）。
 */
public class Ddd4jQuarkusWebFilter {

    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58\u4f1a\u8bdd\u6216\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u7684\u952e\u3002 */
    static final String SESSION_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".session";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 DDD4J \u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u7684\u952e\u3002 */
    static final String CONTEXT_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".context";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 OpenTelemetry \u8ddf\u8e2a Span \u7684\u952e\u3002 */
    static final String OTEL_SPAN_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".otelSpan";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 OpenTelemetry \u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u7684\u952e\u3002 */
    static final String OTEL_SCOPE_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".otelScope";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final WebIdempotencyLifecycle idempotencyLifecycle;

    /** \u5f53\u524d Vert.x \u6216 Quarkus HTTP \u8def\u7531\u4e0a\u4e0b\u6587\u3002 */
    @Inject
    RoutingContext routingContext;

    /** \u4f7f\u7528\u5f53\u524d MicroProfile \u914d\u7f6e\u521b\u5efa\u8fc7\u6ee4\u5668\u3002 */
    public Ddd4jQuarkusWebFilter() {
        this(Ddd4jQuarkusWebConfiguration.load());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a Web \u914d\u7f6e\u521b\u5efa\u8fc7\u6ee4\u5668\u3002
     * @param configuration Web \u914d\u7f6e
     */
    public Ddd4jQuarkusWebFilter(Ddd4jQuarkusWebConfiguration configuration) {
        Ddd4jQuarkusWebConfiguration config = Objects.requireNonNull(configuration,
                "configuration must not be null");
        ClientIpResolver clientIpResolver = config.isTrustForwardedHeaders()
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        this.contextFactory = new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
        this.requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(), config.accessPolicy());
        this.idempotencyLifecycle = config.isIdempotencyEnabled()
                ? new WebIdempotencyLifecycle(new CacheIdempotencyGuard(config.getIdempotencyCacheName()),
                config.getIdempotencyTtl()) : null;
    }

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Ddd4jQuarkusWebFilter</code> \u5b9e\u4f8b\u3002
     * @param contextFactory <code>contextFactory</code> \u5b57\u6bb5
     * @param requestLifecycle \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6
     * @param idempotencyLifecycle \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6
     */
    Ddd4jQuarkusWebFilter(WebRequestContextFactory contextFactory,
                          WebRequestLifecycle requestLifecycle,
                          WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = idempotencyLifecycle;
    }

    /**
     * \u521d\u59cb\u5316\u8bf7\u6c42\u4e0a\u4e0b\u6587\u3001\u8ba4\u8bc1\u4e0e\u5e42\u7b49\u4f1a\u8bdd\uff0c\u5e76\u542f\u52a8\u8ffd\u8e2a\u3002
     * @param request JAX-RS \u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    @ServerRequestFilter(priority = Priorities.AUTHENTICATION)
    public void request(ContainerRequestContext request) {
        // OTel: 启动 SERVER span
        Object span = WebOtelSupport.startServerSpan(
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath(),
                extractRequestHeaders(request));
        AutoCloseable scope = WebOtelSupport.activate(span);
        request.setProperty(OTEL_SPAN_PROPERTY, span);
        request.setProperty(OTEL_SCOPE_PROPERTY, scope);

        try {
            WebRequestContext context = createContext(request);
            SynchronousWebRequestSession session = SynchronousWebRequestSession.open(context, requestLifecycle,
                    idempotencyLifecycle, request.getHeaderString(WebHeaders.IDEMPOTENCY_KEY));
            request.setProperty(CONTEXT_PROPERTY, context);
            request.setProperty(SESSION_PROPERTY, session);
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            throw exception;
        }
    }

    /**
     * \u5b8c\u6210\u8bf7\u6c42\u4f1a\u8bdd\u4e0e\u8ffd\u8e2a\uff0c\u5e76\u5199\u5165\u5173\u8054\u6807\u8bc6\u54cd\u5e94\u5934\u3002
     * @param request JAX-RS \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param response JAX-RS \u54cd\u5e94\u4e0a\u4e0b\u6587
     */
    @ServerResponseFilter(priority = Priorities.USER)
    public void response(ContainerRequestContext request, ContainerResponseContext response) {
        Object contextValue = request.getProperty(CONTEXT_PROPERTY);
        if (contextValue instanceof WebRequestContext context) {
            response.getHeaders().putSingle(WebHeaders.REQUEST_ID, context.requestId());
            response.getHeaders().putSingle(WebHeaders.TRACE_ID, context.traceId());
        }
        Object sessionValue = request.getProperty(SESSION_PROPERTY);
        boolean successful = response.getStatus() < 400;
        if (sessionValue instanceof SynchronousWebRequestSession session) {
            session.complete(successful);
        }
        // OTel: 结束 span
        Object span = request.getProperty(OTEL_SPAN_PROPERTY);
        if (Objects.nonNull(span)) {
            WebOtelSupport.endServerSpan(span, response.getStatus());
            request.removeProperty(OTEL_SPAN_PROPERTY);
        }
        Object scope = request.getProperty(OTEL_SCOPE_PROPERTY);
        if (scope instanceof AutoCloseable) {
            try {
                ((AutoCloseable) scope).close();
            } catch (Throwable ignored) {
            }
            request.removeProperty(OTEL_SCOPE_PROPERTY);
        }
        request.removeProperty(CONTEXT_PROPERTY);
        request.removeProperty(SESSION_PROPERTY);
    }

    /**
     * \u6536\u96c6 HTTP \u8bf7\u6c42\u4e2d\u53ef\u7528\u7684\u5934\u540d\u79f0\u53ca\u5176\u503c\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractRequestHeaders(ContainerRequestContext request) {
        Map<String, String> headers = new HashMap<>();
        for (var entry : request.getHeaders().entrySet()) {
            String value = request.getHeaderString(entry.getKey());
            if (Objects.nonNull(value)) {
                headers.put(entry.getKey(), value);
            }
        }
        return headers;
    }

    /**
     * \u4ece\u6846\u67b6\u8bf7\u6c42\u63d0\u53d6\u79df\u6237\u3001\u8ddf\u8e2a\u6807\u8bc6\u3001\u8ba4\u8bc1\u548c\u8def\u7531\u4fe1\u606f\uff0c\u7ec4\u88c5\u5171\u7528\u4e0a\u4e0b\u6587\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u4f9b\u4e0a\u4e0b\u6587\u548c\u8ba4\u8bc1\u5904\u7406\u94fe\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private WebRequestContext createContext(ContainerRequestContext request) {
        return contextFactory.create(new WebRequestData(
                request.getHeaderString(WebHeaders.REQUEST_ID),
                request.getHeaderString(WebHeaders.TRACE_ID),
                request.getHeaderString(WebHeaders.TENANT_ID),
                request.getHeaderString(WebHeaders.AUTHORIZATION),
                Optional.ofNullable(request.getLanguage()).orElse(Locale.getDefault()),
                request.getHeaderString(WebHeaders.FORWARDED_FOR),
                request.getHeaderString("X-Real-IP"),
                remoteAddress(),
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath()));
    }

    /**
     * \u4ece\u5e95\u5c42 HTTP \u8fde\u63a5\u8bfb\u53d6\u5bf9\u7aef\u5730\u5740\uff0c\u672a\u53d6\u5f97\u8fde\u63a5\u4fe1\u606f\u65f6\u8fd4\u56de\u7a7a\u3002
     * @return \u6846\u67b6\u62a5\u544a\u7684\u5bf9\u7aef\u5730\u5740
     */
    private String remoteAddress() {
        if (Objects.isNull(routingContext) || Objects.isNull(routingContext.request().remoteAddress())) {
            return null;
        }
        return routingContext.request().remoteAddress().hostAddress();
    }
}
