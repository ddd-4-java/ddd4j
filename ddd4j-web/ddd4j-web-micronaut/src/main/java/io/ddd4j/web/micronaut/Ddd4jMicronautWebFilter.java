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
package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator.Authentication;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * Micronaut 4 Filter Method 请求上下文、Bearer Subject 与幂等适配器。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public final class Ddd4jMicronautWebFilter {

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;

    /**
     * 根据 Micronaut Web 配置创建请求过滤器。
     *
     * @param configuration Web 配置
     */
    @Inject
    public Ddd4jMicronautWebFilter(Ddd4jMicronautWebConfiguration configuration) {
        Ddd4jMicronautWebConfiguration config = Objects.requireNonNull(configuration,
                "configuration must not be null");
        ClientIpResolver clientIpResolver = config.isTrustForwardedHeaders()
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        this.contextFactory = new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
        this.requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(config.getPublicPaths(), config.getDefaultAuthenticationMode()));
        this.idempotencyLifecycle = config.isIdempotencyEnabled()
                ? Optional.of(new WebIdempotencyLifecycle(
                new CacheIdempotencyGuard(config.getIdempotencyCacheName()), config.getIdempotencyTtl()))
                : Optional.empty();
    }

    /**
     * 使用指定上下文、认证及幂等组件创建过滤器。
     *
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param idempotencyLifecycle 幂等生命周期，可为空
     */
    public Ddd4jMicronautWebFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                                   WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
    }

    /**
     * \u5c1d\u8bd5\u5173\u95ed\u8bf7\u6c42\u4f5c\u7528\u57df\uff0c\u6e05\u7406\u6545\u969c\u4e0d\u6253\u65ad\u54cd\u5e94\u5904\u7406\u3002
     * @param scope \u9700\u8981\u5728\u5f53\u524d\u8bf7\u6c42\u7ed3\u675f\u540e\u5173\u95ed\u7684\u4f5c\u7528\u57df
     */
    private static void closeScope(AutoCloseable scope) {
        try {
            scope.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * \u63d0\u53d6\u8bf7\u6c42\u5934\uff0c\u6bcf\u4e2a\u6709\u503c\u7684\u8bf7\u6c42\u5934\u4fdd\u7559\u7b2c\u4e00\u4e2a\u503c\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractRequestHeaders(HttpRequest<?> request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((k, v) -> {
            if (Objects.nonNull(v) && !v.isEmpty()) {
                headers.put(k, v.get(0));
            }
        });
        return headers;
    }

    /**
     * 建立请求上下文并在响应或异常后结束会话与追踪。
     *
     * @param request           Micronaut 请求
     * @param continuation      下游过滤链
     * @param propagatedContext 可传播上下文
     * @return 下游响应流
     */
    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)
    public Publisher<MutableHttpResponse<?>> filter(HttpRequest<?> request,
                                                    FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation,
                                                    MutablePropagatedContext propagatedContext) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Map<String, String> headers = extractRequestHeaders(request);
        Object span = WebOtelSupport.startServerSpan(
                request.getMethodName(), request.getPath(), headers);

        WebRequestContext requestContext;
        Optional<Authentication> authentication;
        try {
            requestContext = createContext(request);
            authentication = requestLifecycle.authenticate(requestContext);
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            WebOtelSupport.endServerSpan(span, 500);
            throw exception;
        }
        propagatedContext.add(new Ddd4jMicronautContext(requestContext,
                authentication.map(Authentication::subject)));
        Optional<WebIdempotencyLifecycle.Scope> idempotencyScope = idempotencyLifecycle.flatMap(lifecycle ->
                lifecycle.open(requestContext, request.getHeaders().get(WebHeaders.IDEMPOTENCY_KEY)));
        Publisher<MutableHttpResponse<?>> downstream;
        AutoCloseable otelScope = WebOtelSupport.activate(span);
        try {
            downstream = continuation.proceed();
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            WebOtelSupport.endServerSpan(span, 500);
            closeIdempotency(idempotencyScope, false);
            throw exception;
        } finally {
            closeScope(otelScope);
        }
        return Flux.from(downstream)
                .doOnNext(response -> {
                    addResponseHeaders(response, requestContext);
                    closeIdempotency(idempotencyScope, response.getStatus().getCode() < 400);
                    // OTel: 结束 span
                    WebOtelSupport.endServerSpan(span, response.getStatus().getCode());
                })
                .doOnError(throwable -> {
                    WebOtelSupport.recordError(span, throwable);
                    WebOtelSupport.endServerSpan(span, 500);
                    closeIdempotency(idempotencyScope, false);
                })
                .doOnCancel(() -> {
                    WebOtelSupport.recordError(span, new RuntimeException("cancelled"));
                    WebOtelSupport.endServerSpan(span, 500);
                    closeIdempotency(idempotencyScope, false);
                });
    }

    /**
     * \u4ece\u6846\u67b6\u8bf7\u6c42\u63d0\u53d6\u79df\u6237\u3001\u8ddf\u8e2a\u6807\u8bc6\u3001\u8ba4\u8bc1\u548c\u8def\u7531\u4fe1\u606f\uff0c\u7ec4\u88c5\u5171\u7528\u4e0a\u4e0b\u6587\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u4f9b\u4e0a\u4e0b\u6587\u548c\u8ba4\u8bc1\u5904\u7406\u94fe\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private WebRequestContext createContext(HttpRequest<?> request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String remoteHost = Objects.nonNull(remoteAddress) && Objects.nonNull(remoteAddress.getAddress())
                ? remoteAddress.getAddress().getHostAddress() : "unknown";
        return contextFactory.create(new WebRequestData(
                request.getHeaders().get(WebHeaders.REQUEST_ID),
                request.getHeaders().get(WebHeaders.TRACE_ID),
                request.getHeaders().get(WebHeaders.TENANT_ID),
                request.getHeaders().get(WebHeaders.AUTHORIZATION),
                request.getLocale().orElse(Locale.getDefault()),
                request.getHeaders().get(WebHeaders.FORWARDED_FOR),
                request.getHeaders().get("X-Real-IP"),
                remoteHost,
                request.getMethodName(),
                request.getPath()));
    }

    /**
     * \u628a\u8bf7\u6c42\u6807\u8bc6\u548c\u8ddf\u8e2a\u6807\u8bc6\u5199\u56de Micronaut \u54cd\u5e94\u3002
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void addResponseHeaders(MutableHttpResponse<?> response, WebRequestContext context) {
        response.header(WebHeaders.REQUEST_ID, context.requestId());
        response.header(WebHeaders.TRACE_ID, context.traceId());
    }

    /**
     * \u8bf7\u6c42\u7ed3\u675f\u540e\u6309\u6210\u529f\u72b6\u6001\u5b8c\u6210\u6216\u91ca\u653e\u5df2\u53d6\u5f97\u7684\u5e42\u7b49\u4f5c\u7528\u57df\u3002
     * @param scope \u53ef\u5b8c\u6210\u6216\u91ca\u653e\u7684\u5e42\u7b49\u4f5c\u7528\u57df
     * @param successful \u8bf7\u6c42\u662f\u5426\u6309\u6b63\u5e38\u4e1a\u52a1\u7ed3\u679c\u5b8c\u6210
     */
    private void closeIdempotency(Optional<WebIdempotencyLifecycle.Scope> scope, boolean successful) {
        scope.ifPresent(idempotencyScope -> {
            if (successful) {
                idempotencyScope.complete();
            }
            idempotencyScope.close();
        });
    }
}
