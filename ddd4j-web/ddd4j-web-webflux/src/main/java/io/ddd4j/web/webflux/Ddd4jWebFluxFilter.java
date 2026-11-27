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
package io.ddd4j.web.webflux;
import java.util.Optional;
import java.util.Objects;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.HashMap;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebHeaders;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator.Authentication;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * 使用 Reactor Context 传播请求状态，并将同步 Subject/Cache SPI 调度到阻塞工作线程。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
public final class Ddd4jWebFluxFilter implements WebFilter {

    /** \u4fdd\u5b58\u6846\u67b6\u8bf7\u6c42\u670d\u52a1\u7aef Span \u7684\u952e\u3002 */
    private static final String OTEL_SPAN_KEY = Ddd4jWebFluxFilter.class.getName() + ".otelSpan";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    /** \u5c06\u963b\u585e\u5f0f\u5e42\u7b49\u7f13\u5b58\u8bbf\u95ee\u79fb\u51fa\u54cd\u5e94\u5f0f\u4e8b\u4ef6\u7ebf\u7a0b\u7684\u8c03\u5ea6\u5668\u3002 */
    private final Scheduler blockingScheduler;

    /**
     * \u4f7f\u7528\u5168\u90e8\u8def\u5f84\u9700\u8981\u8ba4\u8bc1\u7684\u9ed8\u8ba4\u7b56\u7565\u521b\u5efa\u8fc7\u6ee4\u5668\u3002
     * @param authenticator Bearer \u4e3b\u4f53\u8ba4\u8bc1\u5668
     */
    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator, WebAccessPolicy.required()), null);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u516c\u5f00\u8def\u5f84\u7b56\u7565\u521b\u5efa\u8fc7\u6ee4\u5668\u3002
     * @param authenticator Bearer \u4e3b\u4f53\u8ba4\u8bc1\u5668
     * @param publicPath \u516c\u5f00\u8def\u5f84\u5224\u5b9a\u5668
     */
    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(publicPath)), null);
    }

    /**
     * \u4f7f\u7528\u9ed8\u8ba4\u963b\u585e\u4efb\u52a1\u8c03\u5ea6\u5668\u521b\u5efa\u8fc7\u6ee4\u5668\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param idempotencyLifecycle \u5e42\u7b49\u751f\u547d\u5468\u671f\uff0c\u53ef\u4e3a\u7a7a
     */
    public Ddd4jWebFluxFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                              WebIdempotencyLifecycle idempotencyLifecycle) {
        this(contextFactory, requestLifecycle, idempotencyLifecycle, Schedulers.boundedElastic());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u963b\u585e\u4efb\u52a1\u8c03\u5ea6\u5668\u521b\u5efa\u8fc7\u6ee4\u5668\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param idempotencyLifecycle \u5e42\u7b49\u751f\u547d\u5468\u671f\uff0c\u53ef\u4e3a\u7a7a
     * @param blockingScheduler \u963b\u585e\u4efb\u52a1\u8c03\u5ea6\u5668
     */
    public Ddd4jWebFluxFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                              WebIdempotencyLifecycle idempotencyLifecycle, Scheduler blockingScheduler) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
        this.blockingScheduler = Objects.requireNonNull(blockingScheduler, "blockingScheduler must not be null");
    }

    /**
     * \u63d0\u53d6\u8bf7\u6c42\u5934\uff0c\u6bcf\u4e2a\u6709\u503c\u7684\u8bf7\u6c42\u5934\u4fdd\u7559\u7b2c\u4e00\u4e2a\u503c\u3002
     * @param exchange \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractHeaders(ServerWebExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequest().getHeaders().forEach((k, v) -> {
            if (Objects.nonNull(v) && !v.isEmpty()) {
                headers.put(k, v.get(0));
            }
        });
        return headers;
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
     * \u5728\u8ba2\u9605\u65f6\u89e3\u6790 WebFlux \u8bf7\u6c42\u5e76\u8bbe\u7f6e\u8ba4\u8bc1\u3001\u5e42\u7b49\u548c\u8ddf\u8e2a\u4e0a\u4e0b\u6587\u3002
     * @param exchange \u5f53\u524d WebFlux \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @param chain \u5f85\u7ee7\u7eed\u6267\u884c\u7684 WebFlux \u8fc7\u6ee4\u5668\u94fe
     * @return \u5728\u6574\u4e2a\u4e0b\u6e38\u8fc7\u6ee4\u5668\u94fe\u7ed3\u675f\u65f6\u5b8c\u6210\u7684\u54cd\u5e94\u5f0f\u4fe1\u53f7
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            Map<String, String> headers = extractHeaders(exchange);
            Object span = WebOtelSupport.startServerSpan(
                    exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getPath().value(),
                    headers);
            if (Objects.nonNull(span)) {
                exchange.getAttributes().put(OTEL_SPAN_KEY, span);
            }
            return Mono.defer(() -> {
                WebRequestContext requestContext = createContext(exchange);
                exchange.getResponse().getHeaders().set(WebHeaders.REQUEST_ID, requestContext.requestId());
                exchange.getResponse().getHeaders().set(WebHeaders.TRACE_ID, requestContext.traceId());
                Mono<Optional<Authentication>> authentication = Mono.fromCallable(
                        () -> requestLifecycle.authenticate(requestContext)).subscribeOn(blockingScheduler);
                Mono<Optional<WebIdempotencyLifecycle.Scope>> idempotency = acquireIdempotency(requestContext,
                        exchange);
                return authentication.flatMap(result -> idempotency.flatMap(idem -> invoke(exchange, chain,
                        requestContext, result, idem, span)));
            }).doFinally(signalType -> {
                Object currentSpan = exchange.getAttributes().remove(OTEL_SPAN_KEY);
                if (Objects.nonNull(currentSpan)) {
                    int status = Objects.nonNull(exchange.getResponse().getStatusCode())
                            ? exchange.getResponse().getStatusCode().value() : 200;
                    WebOtelSupport.endServerSpan(currentSpan, status);
                }
            });
        });
    }

    /**
     * \u5728\u4f20\u64ad\u8ddf\u8e2a\u548c\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u7684\u540c\u65f6\u6267\u884c WebFlux \u8fc7\u6ee4\u5668\u94fe\u3002
     * @param exchange \u5f53\u524d WebFlux \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @param chain \u5f85\u7ee7\u7eed\u6267\u884c\u7684 WebFlux \u8fc7\u6ee4\u5668\u94fe
     * @param requestContext \u5f53\u524d HTTP \u8bf7\u6c42\u5df2\u7ecf\u89e3\u6790\u7684 DDD4J \u4e0a\u4e0b\u6587
     * @param authentication \u5f53\u524d\u8bf7\u6c42\u5df2\u53d6\u5f97\u7684\u8ba4\u8bc1\u7ed3\u679c
     * @param idempotencyScope \u5f53\u524d\u8bf7\u6c42\u83b7\u53d6\u7684\u53ef\u9009\u5e42\u7b49\u4f5c\u7528\u57df
     * @param span \u5f53\u524d\u8bf7\u6c42\u7684\u8ddf\u8e2a Span
     * @return \u8868\u793a\u4e0b\u6e38\u8bf7\u6c42\u6267\u884c\u4e0e\u6e05\u7406\u7684\u54cd\u5e94\u5f0f\u4fe1\u53f7
     */
    private Mono<Void> invoke(ServerWebExchange exchange, WebFilterChain chain, WebRequestContext requestContext,
                              Optional<Authentication> authentication,
                              Optional<WebIdempotencyLifecycle.Scope> idempotencyScope, Object span) {
        Mono<Void> invocation;
        AutoCloseable otelScope = WebOtelSupport.activate(span);
        try {
            invocation = chain.filter(exchange)
                    .contextWrite(context -> context.put(Ddd4jWebFluxContext.REQUEST_CONTEXT_KEY, requestContext));
        } finally {
            closeScope(otelScope);
        }
        if (authentication.isPresent()) {
            invocation = invocation.contextWrite(context -> context.put(Ddd4jWebFluxContext.SUBJECT_KEY,
                    authentication.orElseThrow().subject()));
        }
        if (idempotencyScope.isEmpty()) {
            return invocation;
        }
        WebIdempotencyLifecycle.Scope scope = idempotencyScope.orElseThrow();
        Mono<Void> scopedInvocation = invocation;
        return Mono.usingWhen(Mono.just(scope), ignored -> scopedInvocation,
                ignored -> complete(scope),
                (ignored, throwable) -> release(scope),
                ignored -> release(scope));
    }

    /**
     * \u4ece WebFlux \u8bf7\u6c42\u5934\u8bfb\u53d6\u5e42\u7b49\u952e\u5e76\u5c1d\u8bd5\u53d6\u5f97\u79df\u7ea6\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param exchange \u5f53\u524d WebFlux \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @return \u5df2\u5f00\u542f\u7684\u5e42\u7b49\u4f5c\u7528\u57df\uff0c\u65e0\u9700\u4fdd\u62a4\u65f6\u4e3a\u7a7a
     */
    private Optional<WebIdempotencyLifecycle.Scope> openIdempotency(WebRequestContext context,
                                                                    ServerWebExchange exchange) {
        return idempotencyLifecycle.flatMap(lifecycle -> lifecycle.open(context,
                exchange.getRequest().getHeaders().getFirst(WebHeaders.IDEMPOTENCY_KEY)));
    }

    /**
     * \u5728\u9002\u5408\u963b\u585e\u8c03\u7528\u7684\u8c03\u5ea6\u5668\u4e0a\u83b7\u53d6\u5e42\u7b49\u79df\u7ea6\uff0c\u5e76\u5904\u7406\u8bf7\u6c42\u53d6\u6d88\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param exchange \u5f53\u524d WebFlux \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @return \u53d1\u9001\u53ef\u9009\u5e42\u7b49\u79df\u7ea6\u7684\u54cd\u5e94\u5f0f\u7ed3\u679c
     */
    private Mono<Optional<WebIdempotencyLifecycle.Scope>> acquireIdempotency(WebRequestContext context,
                                                                              ServerWebExchange exchange) {
        return Mono.defer(() -> {
            AtomicBoolean cancelled = new AtomicBoolean();
            AtomicReference<WebIdempotencyLifecycle.Scope> pending = new AtomicReference<>();
            return Mono.fromCallable(() -> {
                Optional<WebIdempotencyLifecycle.Scope> acquired = openIdempotency(context, exchange);
                acquired.ifPresent(pending::set);
                if (cancelled.get()) {
                    WebIdempotencyLifecycle.Scope scope = pending.getAndSet(null);
                    if (Objects.nonNull(scope)) {
                        scope.close();
                    }
                }
                return acquired;
            }).subscribeOn(blockingScheduler).doOnCancel(() -> {
                cancelled.set(true);
                WebIdempotencyLifecycle.Scope scope = pending.getAndSet(null);
                if (Objects.nonNull(scope)) {
                    scope.close();
                }
            });
        });
    }

    /**
     * \u5c06\u5f53\u524d\u8bf7\u6c42\u7684\u5e42\u7b49\u79df\u7ea6\u6807\u8bb0\u4e3a\u6210\u529f\u5b8c\u6210\u3002
     * @param scope \u53ef\u5b8c\u6210\u6216\u91ca\u653e\u7684\u5e42\u7b49\u4f5c\u7528\u57df
     * @return \u5e42\u7b49\u786e\u8ba4\u5b8c\u6210\u65f6\u7ed3\u675f\u7684\u54cd\u5e94\u5f0f\u4fe1\u53f7
     */
    private Mono<Void> complete(WebIdempotencyLifecycle.Scope scope) {
        return Mono.fromRunnable(() -> {
            scope.complete();
            scope.close();
        }).subscribeOn(blockingScheduler).then();
    }

    /**
     * \u5728\u8bf7\u6c42\u672a\u5b8c\u6210\u65f6\u91ca\u653e\u5f53\u524d\u53d6\u5f97\u7684\u5e42\u7b49\u79df\u7ea6\u3002
     * @param scope \u53ef\u5b8c\u6210\u6216\u91ca\u653e\u7684\u5e42\u7b49\u4f5c\u7528\u57df
     * @return \u79df\u7ea6\u91ca\u653e\u5b8c\u6210\u65f6\u7ed3\u675f\u7684\u54cd\u5e94\u5f0f\u4fe1\u53f7
     */
    private Mono<Void> release(WebIdempotencyLifecycle.Scope scope) {
        return Mono.fromRunnable(scope::close).subscribeOn(blockingScheduler).then();
    }

    /**
     * \u4ece\u6846\u67b6\u8bf7\u6c42\u63d0\u53d6\u79df\u6237\u3001\u8ddf\u8e2a\u6807\u8bc6\u3001\u8ba4\u8bc1\u548c\u8def\u7531\u4fe1\u606f\uff0c\u7ec4\u88c5\u5171\u7528\u4e0a\u4e0b\u6587\u3002
     * @param exchange \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u4f9b\u4e0a\u4e0b\u6587\u548c\u8ba4\u8bc1\u5904\u7406\u94fe\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private WebRequestContext createContext(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        return contextFactory.create(new WebRequestData(
                headers.getFirst(WebHeaders.REQUEST_ID),
                headers.getFirst(WebHeaders.TRACE_ID),
                headers.getFirst(WebHeaders.TENANT_ID),
                headers.getFirst(WebHeaders.AUTHORIZATION),
                resolveLocale(headers),
                headers.getFirst(WebHeaders.FORWARDED_FOR),
                headers.getFirst("X-Real-IP"),
                remoteAddress(exchange),
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value()), resolveApiVersion(exchange));
    }

    /**
     * \u6839\u636e\u5f53\u524d Web \u8bf7\u6c42\u89e3\u6790\u9700\u8981\u4f20\u9012\u7684 API \u7248\u672c\u3002
     * @param exchange \u5f53\u524d WebFlux \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @return \u89e3\u6790\u5230\u7684 API \u7248\u672c\uff0c\u7f3a\u5c11\u65f6\u4fdd\u7559\u7f3a\u7701\u884c\u4e3a
     */
    private ApiVersion resolveApiVersion(ServerWebExchange exchange) {
        Object attribute = exchange.getAttributes().get(ApiVersionWebFilter.API_VERSION_ATTRIBUTE);
        return attribute instanceof ApiVersion ? (ApiVersion) attribute : null;
    }

    /**
     * \u4f7f\u7528\u8bf7\u6c42\u8bed\u8a00\u504f\u597d\u5217\u8868\u4e2d\u7684\u7b2c\u4e00\u9879\uff0c\u672a\u63d0\u4f9b\u504f\u597d\u65f6\u7528 JVM \u9ed8\u8ba4\u533a\u57df\u3002
     * @param headers \u63d0\u4f9b\u8bed\u8a00\u504f\u597d\u7684\u8bf7\u6c42\u6216\u8bf7\u6c42\u5934
     * @return \u672c\u6b21\u8bf7\u6c42\u4f7f\u7528\u7684\u533a\u57df
     */
    private Locale resolveLocale(HttpHeaders headers) {
        List<Locale> locales = headers.getAcceptLanguageAsLocales();
        return CollectionUtils.isEmpty(locales) ? Locale.getDefault() : locales.get(0);
    }

    /**
     * \u4ece\u5e95\u5c42 HTTP \u8fde\u63a5\u8bfb\u53d6\u5bf9\u7aef\u5730\u5740\uff0c\u672a\u53d6\u5f97\u8fde\u63a5\u4fe1\u606f\u65f6\u8fd4\u56de\u7a7a\u3002
     * @param exchange \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u6846\u67b6\u62a5\u544a\u7684\u5bf9\u7aef\u5730\u5740
     */
    private String remoteAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (Objects.isNull(remoteAddress)) {
            return null;
        }
        return Objects.nonNull(remoteAddress.getAddress())
                ? remoteAddress.getAddress().getHostAddress() : remoteAddress.getHostString();
    }
}
