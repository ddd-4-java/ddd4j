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

    /**
     * 请求属性中保存会话或认证上下文的键。
     */
    static final String SESSION_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".session";
    /**
     * 请求属性中保存 DDD4J 上下文作用域的键。
     */
    static final String CONTEXT_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".context";
    /**
     * 请求属性中保存 OpenTelemetry 跟踪 Span 的键。
     */
    static final String OTEL_SPAN_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".otelSpan";
    /**
     * 请求属性中保存 OpenTelemetry 上下文作用域的键。
     */
    static final String OTEL_SCOPE_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".otelScope";

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
     * 当前 Vert.x 或 Quarkus HTTP 路由上下文。
     */
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
     * 用给定值初始化 <code>Ddd4jQuarkusWebFilter</code> 实例。
     *
     * @param contextFactory       <code>contextFactory</code> 字段
     * @param requestLifecycle     开启和关闭当前 Web 请求上下文的组件
     * @param idempotencyLifecycle 在请求处理阶段获取及释放幂等保护的组件
     */
    Ddd4jQuarkusWebFilter(WebRequestContextFactory contextFactory,
                          WebRequestLifecycle requestLifecycle,
                          WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = idempotencyLifecycle;
    }

    /**
     * 尝试关闭请求作用域，清理故障不打断响应处理。
     *
     * @param scope 需要在当前请求结束后关闭的作用域
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
     * 收集 HTTP 请求中可用的头名称及其值。
     *
     * @param request 当前框架 HTTP 请求或交换上下文
     * @return 供请求上下文读取的请求头映射
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
                remoteAddress(),
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath()));
    }

    /**
     * 从底层 HTTP 连接读取对端地址，未取得连接信息时返回空。
     *
     * @return 框架报告的对端地址
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
