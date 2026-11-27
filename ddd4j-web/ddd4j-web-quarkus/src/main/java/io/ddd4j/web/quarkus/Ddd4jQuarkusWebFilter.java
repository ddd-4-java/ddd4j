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
package io.ddd4j.web.quarkus;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.util.*;

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

    /**
     * 使用当前 MicroProfile 配置创建过滤器。
     */
    public Ddd4jQuarkusWebFilter() {
        this(Ddd4jQuarkusWebConfiguration.load());
    }

    /**
     * 使用指定 Web 配置创建过滤器。
     *
     * @param configuration Web 配置
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
     * \u5c1d\u8bd5\u5173\u95ed\u8bf7\u6c42\u4f5c\u7528\u57df\uff0c\u6e05\u7406\u6545\u969c\u4e0d\u6253\u65ad\u54cd\u5e94\u5904\u7406\u3002
     * @param scope \u9700\u8981\u5728\u5f53\u524d\u8bf7\u6c42\u7ed3\u675f\u540e\u5173\u95ed\u7684\u4f5c\u7528\u57df
     */
    private static void closeScope(Object scope) {
        if (scope instanceof AutoCloseable) {
            try {
                ((AutoCloseable) scope).close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * \u6536\u96c6 HTTP \u8bf7\u6c42\u4e2d\u53ef\u7528\u7684\u5934\u540d\u79f0\u53ca\u5176\u503c\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractRequestHeaders(ContainerRequestContext request) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
            String value = request.getHeaderString(entry.getKey());
            if (Objects.nonNull(value)) {
                headers.put(entry.getKey(), value);
            }
        }
        return headers;
    }

    /**
     * 初始化请求上下文、认证与幂等会话，并启动追踪。
     *
     * @param request JAX-RS 请求上下文
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

        SynchronousWebRequestSession session = null;
        try {
            WebRequestContext context = createContext(request);
            session = SynchronousWebRequestSession.open(context, requestLifecycle,
                    idempotencyLifecycle, request.getHeaderString(WebHeaders.IDEMPOTENCY_KEY));
            request.setProperty(CONTEXT_PROPERTY, context);
            request.setProperty(SESSION_PROPERTY, session);
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            if (Objects.nonNull(session)) {
                try {
                    session.complete(false);
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            int status = new DefaultWebExceptionTranslator().translate(exception).status();
            WebOtelSupport.endServerSpan(span, status);
            closeScope(scope);
            request.removeProperty(OTEL_SPAN_PROPERTY);
            request.removeProperty(OTEL_SCOPE_PROPERTY);
            request.removeProperty(CONTEXT_PROPERTY);
            request.removeProperty(SESSION_PROPERTY);
            throw exception;
        }
    }

    /**
     * 完成请求会话与追踪，并写入关联标识响应头。
     *
     * @param request  JAX-RS 请求上下文
     * @param response JAX-RS 响应上下文
     */
    @ServerResponseFilter(priority = Priorities.USER)
    public void response(ContainerRequestContext request, ContainerResponseContext response) {
        Object span = request.getProperty(OTEL_SPAN_PROPERTY);
        Object scope = request.getProperty(OTEL_SCOPE_PROPERTY);
        try {
            Object contextValue = request.getProperty(CONTEXT_PROPERTY);
            if (contextValue instanceof WebRequestContext) {
                WebRequestContext context = (WebRequestContext) contextValue;
                response.getHeaders().putSingle(WebHeaders.REQUEST_ID, context.requestId());
                response.getHeaders().putSingle(WebHeaders.TRACE_ID, context.traceId());
            }
            Object sessionValue = request.getProperty(SESSION_PROPERTY);
            boolean successful = response.getStatus() < 400;
            if (sessionValue instanceof SynchronousWebRequestSession) {
                SynchronousWebRequestSession session = (SynchronousWebRequestSession) sessionValue;
                session.complete(successful);
            }
        } finally {
            if (Objects.nonNull(span)) {
                WebOtelSupport.endServerSpan(span, response.getStatus());
            }
            closeScope(scope);
            request.removeProperty(OTEL_SPAN_PROPERTY);
            request.removeProperty(OTEL_SCOPE_PROPERTY);
            request.removeProperty(CONTEXT_PROPERTY);
            request.removeProperty(SESSION_PROPERTY);
        }
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
        if (Objects.isNull(routingContext)) {
            return null;
        }
        SocketAddress address = routingContext.request().remoteAddress();
        if (Objects.isNull(address)) {
            return null;
        }
        String hostAddress = address.hostAddress();
        return Objects.nonNull(hostAddress) ? hostAddress : address.host();
    }
}
