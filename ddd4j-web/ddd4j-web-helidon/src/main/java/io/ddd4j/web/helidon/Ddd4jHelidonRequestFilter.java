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
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.*;

/**
 * Helidon MP 请求上下文与 Bearer Subject 过滤器。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public final class Ddd4jHelidonRequestFilter implements ContainerRequestFilter {

    /**
     * 请求属性中保存会话或认证上下文的键。
     */
    static final String SESSION_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".session";
    /**
     * 请求属性中保存 DDD4J 上下文作用域的键。
     */
    static final String CONTEXT_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".context";
    /**
     * 请求属性中保存 OpenTelemetry 跟踪 Span 的键。
     */
    static final String OTEL_SPAN_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".otelSpan";
    /**
     * 请求属性中保存 OpenTelemetry 上下文作用域的键。
     */
    static final String OTEL_SCOPE_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".otelScope";

    /**
     * 根据框架请求元数据装配共用 Web 请求上下文。
     */
    private final WebRequestContextFactory contextFactory;
    /**
     * 开启和关闭当前 Web 请求上下文的组件。
     */
    private final WebRequestLifecycle requestLifecycle;
    /**
     * 在请求处理阶段获取及释放幂等保护的组件。
     */
    private final WebIdempotencyLifecycle idempotencyLifecycle;

    /**
     * 使用当前 MicroProfile 配置创建请求过滤器。
     */
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
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
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
     * 提取请求头，每个有值的请求头保留第一个值。
     *
     * @param request 当前框架 HTTP 请求或交换上下文
     * @return 供请求上下文读取的请求头映射
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
     * 开启 Helidon 请求跟踪，解析请求元数据并装配认证及幂等保护。
     *
     * @param request 当前进入调用链的 HTTP 请求
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
     * 从框架请求提取租户、跟踪标识、认证和路由信息，组装共用上下文。
     *
     * @param request 当前框架 HTTP 请求
     * @return 供上下文和认证处理链使用的请求上下文
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
