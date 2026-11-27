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
package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;

import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Dropwizard Jersey 请求上下文、Bearer Subject 与幂等过滤器。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Priority(Priorities.AUTHENTICATION)
public final class Ddd4jDropwizardRequestFilter implements ContainerRequestFilter {

    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58\u4f1a\u8bdd\u6216\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u7684\u952e\u3002 */
    static final String SESSION_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".session";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 DDD4J \u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u7684\u952e\u3002 */
    static final String CONTEXT_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".context";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 OpenTelemetry \u8ddf\u8e2a Span \u7684\u952e\u3002 */
    static final String OTEL_SPAN_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".otelSpan";
    /** \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58 OpenTelemetry \u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u7684\u952e\u3002 */
    static final String OTEL_SCOPE_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".otelScope";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final WebIdempotencyLifecycle idempotencyLifecycle;

    /** \u8bfb\u53d6\u8fde\u63a5\u5730\u5740\u53ca Servlet \u8bf7\u6c42\u5c5e\u6027\u7684\u539f\u751f\u8bf7\u6c42\u3002 */
    @Context
    private HttpServletRequest servletRequest;

    /** 使用默认 Web 配置创建请求过滤器。 */
    public Ddd4jDropwizardRequestFilter() {
        this(new Ddd4jDropwizardWebConfiguration());
    }

    /**
     * 使用指定 Web 配置创建请求过滤器。
     *
     * @param configuration Web 配置
     */
    public Ddd4jDropwizardRequestFilter(Ddd4jDropwizardWebConfiguration configuration) {
        Ddd4jDropwizardWebConfiguration config = Objects.requireNonNull(configuration,
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
    public Ddd4jDropwizardRequestFilter(WebRequestContextFactory contextFactory,
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
     * 过滤数据。
     * @param request 请求对象。
     */
    @Override
    public void filter(ContainerRequestContext request) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
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
        if (Objects.isNull(servletRequest)) {
            return null;
        }
        String address = servletRequest.getRemoteAddr();
        int port = servletRequest.getRemotePort();
        return Objects.nonNull(address) ? new InetSocketAddress(address, port).getHostString() : null;
    }
}
