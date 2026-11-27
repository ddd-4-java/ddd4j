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

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator.Authentication;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.version.ApiVersion;
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

    /**
     * 保存框架请求服务端 Span 的键。
     */
    private static final String OTEL_SPAN_KEY = Ddd4jWebFluxFilter.class.getName() + ".otelSpan";

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
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    /**
     * 将阻塞式幂等缓存访问移出响应式事件线程的调度器。
     */
    private final Scheduler blockingScheduler;

    /**
     * 使用全部路径需要认证的默认策略创建过滤器。
     *
     * @param authenticator Bearer 主体认证器
     */
    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator, WebAccessPolicy.required()), null);
    }

    /**
     * 使用指定公开路径策略创建过滤器。
     *
     * @param authenticator Bearer 主体认证器
     * @param publicPath    公开路径判定器
     */
    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(publicPath)), null);
    }

    /**
     * 使用默认阻塞任务调度器创建过滤器。
     *
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param idempotencyLifecycle 幂等生命周期，可为空
     */
    public Ddd4jWebFluxFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                              WebIdempotencyLifecycle idempotencyLifecycle) {
        this(contextFactory, requestLifecycle, idempotencyLifecycle, Schedulers.boundedElastic());
    }

    /**
     * 使用指定阻塞任务调度器创建过滤器。
     *
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param idempotencyLifecycle 幂等生命周期，可为空
     * @param blockingScheduler    阻塞任务调度器
     */
    public Ddd4jWebFluxFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                              WebIdempotencyLifecycle idempotencyLifecycle, Scheduler blockingScheduler) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
        this.blockingScheduler = Objects.requireNonNull(blockingScheduler, "blockingScheduler must not be null");
    }

    /**
     * 提取请求头，每个有值的请求头保留第一个值。
     *
     * @param exchange 当前框架 HTTP 请求或交换上下文
     * @return 供请求上下文读取的请求头映射
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
     * 尝试关闭请求作用域，清理故障不打断响应处理。
     *
     * @param scope 需要在当前请求结束后关闭的作用域
     */
    private static void closeScope(AutoCloseable scope) {
        try {
            scope.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 在订阅时解析 WebFlux 请求并设置认证、幂等和跟踪上下文。
     *
     * @param exchange 当前 WebFlux 请求及响应交换对象
     * @param chain    待继续执行的 WebFlux 过滤器链
     * @return 在整个下游过滤器链结束时完成的响应式信号
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
     * 在传播跟踪和认证上下文的同时执行 WebFlux 过滤器链。
     *
     * @param exchange         当前 WebFlux 请求及响应交换对象
     * @param chain            待继续执行的 WebFlux 过滤器链
     * @param requestContext   当前 HTTP 请求已经解析的 DDD4J 上下文
     * @param authentication   当前请求已取得的认证结果
     * @param idempotencyScope 当前请求获取的可选幂等作用域
     * @param span             当前请求的跟踪 Span
     * @return 表示下游请求执行与清理的响应式信号
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
     * 从 WebFlux 请求头读取幂等键并尝试取得租约。
     *
     * @param context  当前框架调用所携带的请求上下文
     * @param exchange 当前 WebFlux 请求及响应交换对象
     * @return 已开启的幂等作用域，无需保护时为空
     */
    private Optional<WebIdempotencyLifecycle.Scope> openIdempotency(WebRequestContext context,
                                                                    ServerWebExchange exchange) {
        return idempotencyLifecycle.flatMap(lifecycle -> lifecycle.open(context,
                exchange.getRequest().getHeaders().getFirst(WebHeaders.IDEMPOTENCY_KEY)));
    }

    /**
     * 在适合阻塞调用的调度器上获取幂等租约，并处理请求取消。
     *
     * @param context  当前框架调用所携带的请求上下文
     * @param exchange 当前 WebFlux 请求及响应交换对象
     * @return 发送可选幂等租约的响应式结果
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
     * 将当前请求的幂等租约标记为成功完成。
     *
     * @param scope 可完成或释放的幂等作用域
     * @return 幂等确认完成时结束的响应式信号
     */
    private Mono<Void> complete(WebIdempotencyLifecycle.Scope scope) {
        return Mono.fromRunnable(() -> {
            scope.complete();
            scope.close();
        }).subscribeOn(blockingScheduler).then();
    }

    /**
     * 在请求未完成时释放当前取得的幂等租约。
     *
     * @param scope 可完成或释放的幂等作用域
     * @return 租约释放完成时结束的响应式信号
     */
    private Mono<Void> release(WebIdempotencyLifecycle.Scope scope) {
        return Mono.fromRunnable(scope::close).subscribeOn(blockingScheduler).then();
    }

    /**
     * 从框架请求提取租户、跟踪标识、认证和路由信息，组装共用上下文。
     *
     * @param exchange 当前框架 HTTP 请求
     * @return 供上下文和认证处理链使用的请求上下文
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
     * 根据当前 Web 请求解析需要传递的 API 版本。
     *
     * @param exchange 当前 WebFlux 请求及响应交换对象
     * @return 解析到的 API 版本，缺少时保留缺省行为
     */
    private ApiVersion resolveApiVersion(ServerWebExchange exchange) {
        Object attribute = exchange.getAttributes().get(ApiVersionWebFilter.API_VERSION_ATTRIBUTE);
        return attribute instanceof ApiVersion ? (ApiVersion) attribute : null;
    }

    /**
     * 使用请求语言偏好列表中的第一项，未提供偏好时用 JVM 默认区域。
     *
     * @param headers 提供语言偏好的请求或请求头
     * @return 本次请求使用的区域
     */
    private Locale resolveLocale(HttpHeaders headers) {
        List<Locale> locales = headers.getAcceptLanguageAsLocales();
        return CollectionUtils.isEmpty(locales) ? Locale.getDefault() : locales.get(0);
    }

    /**
     * 从底层 HTTP 连接读取对端地址，未取得连接信息时返回空。
     *
     * @param exchange 当前框架 HTTP 请求
     * @return 框架报告的对端地址
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
