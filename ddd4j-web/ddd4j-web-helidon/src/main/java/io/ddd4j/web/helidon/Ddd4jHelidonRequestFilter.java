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
package io.ddd4j.web.helidon;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.RequestIdGenerator;
import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Helidon MP 请求上下文与 Bearer Subject 过滤器。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public final class Ddd4jHelidonRequestFilter implements ContainerRequestFilter {

    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58\u4f1a\u8bdd\u6216\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u7684\u952e\u3002 */
    static final String SESSION_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".session";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 DDD4J \u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u7684\u952e\u3002 */
    static final String CONTEXT_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".context";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 OpenTelemetry \u8ddf\u8e2a Span \u7684\u952e\u3002 */
    static final String OTEL_SPAN_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".otelSpan";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 OpenTelemetry \u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u7684\u952e\u3002 */
    static final String OTEL_SCOPE_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".otelScope";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final WebIdempotencyLifecycle idempotencyLifecycle;

    /** 使用当前 MicroProfile 配置创建请求过滤器。 */
    public Ddd4jHelidonRequestFilter() {
        this(Ddd4jHelidonWebConfiguration.load());
    }

    /**
     * 使用指定 Web 配置创建请求过滤器。
     *
     * @param configuration Web 配置
     */
    public Ddd4jHelidonRequestFilter(Ddd4jHelidonWebConfiguration configuration) {
        Ddd4jHelidonWebConfiguration config = Objects.requireNonNull(configuration,
                "configuration must not be null");
        ClientIpResolver clientIpResolver = config.isTrustForwardedHeaders()
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        this.contextFactory = new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
        this.requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(config.getPublicPaths(), config.getDefaultAuthenticationMode()));
        this.idempotencyLifecycle = config.isIdempotencyEnabled()
                ? new WebIdempotencyLifecycle(new CacheIdempotencyGuard(config.getIdempotencyCacheName()),
                config.getIdempotencyTtl()) : null;
    }

    /**
     * 使用指定请求组件创建过滤器。
     *
     * @param contextFactory 请求上下文工厂
     * @param requestLifecycle 请求生命周期
     * @param idempotencyLifecycle 幂等生命周期，可为空
     */
    public Ddd4jHelidonRequestFilter(WebRequestContextFactory contextFactory,
                                     WebRequestLifecycle requestLifecycle,
                                     WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = idempotencyLifecycle;
    }

    /**
     * \u63d0\u53d6\u8bf7\u6c42\u5934\uff0c\u6bcf\u4e2a\u6709\u503c\u7684\u8bf7\u6c42\u5934\u4fdd\u7559\u7b2c\u4e00\u4e2a\u503c\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractRequestHeaders(ContainerRequestContext request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((k, v) -> {
            if (Objects.nonNull(v) && !v.isEmpty()) {
                headers.put(k, v.get(0));
            }
        });
        return headers;
    }

    /**
     * \u5f00\u542f Helidon \u8bf7\u6c42\u8ddf\u8e2a\uff0c\u89e3\u6790\u8bf7\u6c42\u5143\u6570\u636e\u5e76\u88c5\u914d\u8ba4\u8bc1\u53ca\u5e42\u7b49\u4fdd\u62a4\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     */
    @Override
    public void filter(ContainerRequestContext request) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Object span = WebOtelSupport.startServerSpan(
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath(),
                extractRequestHeaders(request));
        AutoCloseable scope = WebOtelSupport.activate(span);
        if (Objects.nonNull(span)) {
            request.setProperty(OTEL_SPAN_PROPERTY, span);
        }
        if (Objects.nonNull(scope)) {
            request.setProperty(OTEL_SCOPE_PROPERTY, scope);
        }

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
                null,
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath()));
    }
}
