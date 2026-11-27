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
package io.ddd4j.web.vertx;
import java.util.Optional;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.HashMap;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebHeaders;

import java.util.Objects;

import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.health.ReadinessResponse;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;

/**
 * Vert.x Router 的标准请求上下文、认证、幂等和异常处理链。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Slf4j
public final class Ddd4jVertxWeb {

    /** \u4fdd\u5b58 Vert.x \u8bf7\u6c42\u5904\u7406\u72b6\u6001\u548c\u6e05\u7406\u53e5\u67c4\u7684\u952e\u3002 */
    private static final String STATE_KEY = Ddd4jVertxWeb.class.getName() + ".state";
    /** \u4fdd\u5b58\u6846\u67b6\u8bf7\u6c42\u670d\u52a1\u7aef Span \u7684\u952e\u3002 */
    private static final String OTEL_SPAN_KEY = Ddd4jVertxWeb.class.getName() + ".otelSpan";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5c06\u6846\u67b6\u5f02\u5e38\u6620\u5c04\u4e3a\u7edf\u4e00 WebError \u7684\u8f6c\u6362\u5668\u3002 */
    private final WebExceptionTranslator exceptionTranslator;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    /** \u5c06 Vert.x \u5e94\u7528\u7ed3\u679c\u5199\u6210 HTTP JSON \u6b63\u6587\u7684\u7f16\u7801\u5668\u3002 */
    private final Function<Object, String> jsonEncoder;
    /** \u63d0\u4f9b\u4f9d\u8d56\u5c31\u7eea\u72b6\u6001\u7684 Web \u7aef\u70b9\u3002 */
    private final ReadinessEndpoint readinessEndpoint;

    /**
     * 使用默认认证、错误处理和就绪配置创建 Web 适配器。
     */
    public Ddd4jVertxWeb() {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, Json::encode, new RuntimeReadinessRegistry());
    }

    /**
     * 使用应用 Runtime 的 registry 安装标准 readiness 端点。
     *
     * @param readinessRegistry 应用 Runtime 管理的就绪状态注册表
     */
    public Ddd4jVertxWeb(RuntimeReadinessRegistry readinessRegistry) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, Json::encode, readinessRegistry);
    }

    /**
     * 使用指定 Web 组件和默认就绪注册表创建适配器。
     *
     * @param contextFactory 请求上下文工厂
     * @param requestLifecycle 请求生命周期
     * @param exceptionTranslator 异常翻译器
     * @param idempotencyLifecycle 幂等生命周期，可为空
     * @param jsonEncoder JSON 编码器
     */
    public Ddd4jVertxWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                         WebExceptionTranslator exceptionTranslator,
                         WebIdempotencyLifecycle idempotencyLifecycle,
                         Function<Object, String> jsonEncoder) {
        this(contextFactory, requestLifecycle, exceptionTranslator, idempotencyLifecycle, jsonEncoder,
                new RuntimeReadinessRegistry());
    }

    /**
     * 使用指定 Web 组件和就绪注册表创建适配器。
     *
     * @param contextFactory 请求上下文工厂
     * @param requestLifecycle 请求生命周期
     * @param exceptionTranslator 异常翻译器
     * @param idempotencyLifecycle 幂等生命周期，可为空
     * @param jsonEncoder JSON 编码器
     * @param readinessRegistry 就绪注册表
     */
    public Ddd4jVertxWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                         WebExceptionTranslator exceptionTranslator,
                         WebIdempotencyLifecycle idempotencyLifecycle,
                         Function<Object, String> jsonEncoder,
                         RuntimeReadinessRegistry readinessRegistry) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
        this.jsonEncoder = Objects.requireNonNull(jsonEncoder, "jsonEncoder must not be null");
        RuntimeReadinessRegistry registry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
        this.readinessEndpoint = new ReadinessEndpoint(() -> registry.readiness().ready());
    }

    /**
     * \u6536\u96c6 HTTP \u8bf7\u6c42\u4e2d\u53ef\u7528\u7684\u5934\u540d\u79f0\u53ca\u5176\u503c\u3002
     * @param context \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractHeaders(RoutingContext context) {
        Map<String, String> headers = new HashMap<>();
        context.request().headers().forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();
            if (Objects.nonNull(key) && Objects.nonNull(value)) {
                headers.put(key, value);
            }
        });
        return headers;
    }

    /**
     * \u6fc0\u6d3b\u670d\u52a1\u7aef\u8ddf\u8e2a\u4e0a\u4e0b\u6587\u540e\u8fd0\u884c\u5f53\u524d\u56de\u8c03\u5e76\u91ca\u653e\u4f5c\u7528\u57df\u3002
     * @param span \u5f53\u524d\u8bf7\u6c42\u7684\u8ddf\u8e2a Span
     * @param action \u9700\u8981\u5728\u5df2\u6fc0\u6d3b\u8ddf\u8e2a\u4e0a\u4e0b\u6587\u4e2d\u6267\u884c\u7684\u64cd\u4f5c
     */
    private static void runWithSpan(Object span, Runnable action) {
        AutoCloseable scope = WebOtelSupport.activate(span);
        try {
            action.run();
        } finally {
            try {
                scope.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 将请求上下文、认证、错误处理及就绪端点安装到路由器。
     *
     * @param router Vert.x 路由器
     */
    public void install(Router router) {
        Router target = Objects.requireNonNull(router, "router must not be null");
        target.route().handler(contextHandler());
        target.route().handler(authenticationHandler());
        target.route().failureHandler(failureHandler());
        target.get(ReadinessEndpoint.PATH).handler(this::readiness);
    }

    /**
     * \u5c06\u5f53\u524d\u8fd0\u884c\u65f6\u5c31\u7eea\u72b6\u6001\u5199\u5165 Vert.x \u54cd\u5e94\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void readiness(RoutingContext context) {
        ReadinessResponse response = readinessEndpoint.readiness();
        context.response().setStatusCode(response.httpStatus())
                .putHeader("Content-Type", "application/json")
                .end(jsonEncoder.apply(response));
    }

    /**
     * 创建初始化请求上下文与追踪的处理器。
     *
     * @return 请求上下文处理器
     */
    public Handler<RoutingContext> contextHandler() {
        return routingContext -> {
            // OTel: 提取上游 TraceContext 并开启 SERVER span
            Map<String, String> headers = extractHeaders(routingContext);
            Object span = WebOtelSupport.startServerSpan(
                    routingContext.request().method().name(),
                    routingContext.normalizedPath(),
                    headers);
            routingContext.put(OTEL_SPAN_KEY, span);
            routingContext.put(STATE_KEY, new RequestState());
            routingContext.addEndHandler(ignored -> finish(routingContext));

            WebRequestContext requestContext = createContext(routingContext);
            Ddd4jVertxContext.bindRequest(routingContext, requestContext);
            routingContext.response().putHeader(WebHeaders.REQUEST_ID, requestContext.requestId());
            routingContext.response().putHeader(WebHeaders.TRACE_ID, requestContext.traceId());
            runWithSpan(span, routingContext::next);
        };
    }

    /**
     * 创建认证与幂等检查处理器。
     *
     * @return 认证处理器
     */
    public Handler<RoutingContext> authenticationHandler() {
        return routingContext -> {
            WebRequestContext requestContext = Ddd4jVertxContext.request(routingContext).orElseThrow();
            routingContext.vertx().executeBlocking(() -> authenticate(routingContext, requestContext))
                    .onComplete(result -> routingContext.vertx().runOnContext(ignored -> {
                        RequestState state = routingContext.get(STATE_KEY);
                        if (result.failed()) {
                            if (Objects.nonNull(state)) {
                                routingContext.fail(result.cause());
                            }
                            return;
                        }
                        AuthenticationResult authenticationResult = result.result();
                        if (Objects.isNull(state)) {
                            // 请求已结束，业务尚未执行。由工作线程释放迟到的租约。
                            authenticationResult.idempotencyScope().ifPresent(scope ->
                                    routingContext.vertx().executeBlocking(() -> {
                                        scope.close();
                                        return null;
                                    }).onFailure(exception -> log.error("Unable to release unused Vert.x lease", exception)));
                            return;
                        }
                        authenticationResult.authentication().ifPresent(authentication ->
                                Ddd4jVertxContext.bindSubject(routingContext, authentication.subject()));
                        authenticationResult.idempotencyScope().ifPresent(state::idempotencyScope);
                        runWithSpan(routingContext.get(OTEL_SPAN_KEY), routingContext::next);
                    }));
        };
    }

    /**
     * 创建统一异常翻译和响应处理器。
     *
     * @return 异常处理器
     */
    public Handler<RoutingContext> failureHandler() {
        return routingContext -> {
            Throwable failure = Objects.nonNull(routingContext.failure())
                    ? routingContext.failure()
                    : new IllegalStateException("HTTP request failed with status " + routingContext.statusCode());
            RequestState state = routingContext.get(STATE_KEY);
            if (Objects.nonNull(state)) {
                state.failed();
            }
            WebError error = exceptionTranslator.translate(failure);
            if (error.status() >= 500) {
                log.error("Unhandled Vert.x request failure: {} {}", routingContext.request().method(),
                        routingContext.normalizedPath(), failure);
            }
            // OTel: 记录异常
            Object span = routingContext.get(OTEL_SPAN_KEY);
            if (Objects.nonNull(span)) {
                WebOtelSupport.recordError(span, failure);
            }
            runWithSpan(span, () -> {
                if (!routingContext.response().ended()) {
                    routingContext.response().setStatusCode(error.status())
                            .putHeader("Content-Type", "application/json")
                            .end(jsonEncoder.apply(error.toResponse()));
                }
            });
        };
    }

    /**
     * \u6309\u8def\u7531\u8bbf\u95ee\u7b56\u7565\u89e3\u6790\u4e3b\u4f53\u5e76\u53d6\u5f97\u9700\u8981\u7684\u5e42\u7b49\u4fdd\u62a4\u3002
     * @param routingContext \u5f53\u524d Vert.x \u8def\u7531\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param requestContext \u5f53\u524d HTTP \u8bf7\u6c42\u5df2\u7ecf\u89e3\u6790\u7684 DDD4J \u4e0a\u4e0b\u6587
     * @return \u8ba4\u8bc1\u4e0e\u53ef\u7528\u5e42\u7b49\u4f5c\u7528\u57df\u7684\u7ec4\u5408
     */
    private AuthenticationResult authenticate(RoutingContext routingContext, WebRequestContext requestContext) {
        Optional<BearerSubjectAuthenticator.Authentication> authentication = requestLifecycle
                .authenticate(requestContext);
        Optional<WebIdempotencyLifecycle.Scope> idempotencyScope = idempotencyLifecycle.flatMap(lifecycle ->
                lifecycle.open(requestContext,
                        routingContext.request().getHeader(WebHeaders.IDEMPOTENCY_KEY)));
        return new AuthenticationResult(authentication, idempotencyScope);
    }

    /**
     * \u4ece\u6846\u67b6\u8bf7\u6c42\u63d0\u53d6\u79df\u6237\u3001\u8ddf\u8e2a\u6807\u8bc6\u3001\u8ba4\u8bc1\u548c\u8def\u7531\u4fe1\u606f\uff0c\u7ec4\u88c5\u5171\u7528\u4e0a\u4e0b\u6587\u3002
     * @param context \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u4f9b\u4e0a\u4e0b\u6587\u548c\u8ba4\u8bc1\u5904\u7406\u94fe\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private WebRequestContext createContext(RoutingContext context) {
        Locale locale = Objects.isNull(context.preferredLanguage())
                ? Locale.getDefault() : Locale.forLanguageTag(context.preferredLanguage().tag());
        return contextFactory.create(new WebRequestData(
                context.request().getHeader(WebHeaders.REQUEST_ID),
                context.request().getHeader(WebHeaders.TRACE_ID),
                context.request().getHeader(WebHeaders.TENANT_ID),
                context.request().getHeader(WebHeaders.AUTHORIZATION),
                locale,
                context.request().getHeader(WebHeaders.FORWARDED_FOR),
                context.request().getHeader("X-Real-IP"),
                context.request().remoteAddress().host(),
                context.request().method().name(),
                context.normalizedPath()));
    }

    /**
     * \u6839\u636e\u6700\u7ec8\u54cd\u5e94\u7ed3\u675f\u8ddf\u8e2a\u5e76\u6e05\u7406 Vert.x \u8bf7\u6c42\u4f5c\u7528\u57df\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void finish(RoutingContext context) {
        RequestState state = context.remove(STATE_KEY);
        if (Objects.isNull(state)) {
            return;
        }
        boolean successful = !state.failed && context.response().getStatusCode() < 400;
        Object span = context.remove(OTEL_SPAN_KEY);
        if (Objects.nonNull(span)) {
            int status = context.response().getStatusCode() > 0
                    ? context.response().getStatusCode() : 200;
            WebOtelSupport.endServerSpan(span, status);
        }
        context.vertx().executeBlocking(() -> {
            state.close(successful);
            return null;
        }).onFailure(exception -> log.error("Unable to close Vert.x request state", exception));
    }

    /** \u4fdd\u5b58 Vert.x \u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u7ed3\u679c\u53ca\u5df2\u53d6\u5f97\u7684\u5e42\u7b49\u4f5c\u7528\u57df\u3002 */
    private static final class AuthenticationResult {

        /** \u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u7ed3\u679c\uff0c\u516c\u5f00\u8def\u7531\u53ef\u4ee5\u4e0d\u643a\u5e26\u4e3b\u4f53\u3002 */
        private final Optional<BearerSubjectAuthenticator.Authentication> authentication;

        /** \u5f53\u524d\u8bf7\u6c42\u53d6\u5f97\u7684\u5e42\u7b49\u5904\u7406\u4f5c\u7528\u57df\u3002 */
        private final Optional<WebIdempotencyLifecycle.Scope> idempotencyScope;

        /**
         * \u5c06\u8fdb\u5165\u8def\u7531\u524d\u53d6\u5f97\u7684\u8ba4\u8bc1\u7ed3\u679c\u548c\u5e42\u7b49\u4f5c\u7528\u57df\u4e00\u8d77\u4fdd\u5b58\u3002
         * @param authentication \u5f53\u524d\u8bf7\u6c42\u5df2\u53d6\u5f97\u7684\u8ba4\u8bc1\u7ed3\u679c
         * @param idempotencyScope \u5f53\u524d\u8bf7\u6c42\u83b7\u53d6\u7684\u53ef\u9009\u5e42\u7b49\u4f5c\u7528\u57df
         */
        private AuthenticationResult(Optional<BearerSubjectAuthenticator.Authentication> authentication,
                                     Optional<WebIdempotencyLifecycle.Scope> idempotencyScope) {
            this.authentication = authentication;
            this.idempotencyScope = idempotencyScope;
        }

        /**
         * \u8bfb\u53d6<code>authentication</code> \u5b57\u6bb5\u3002
         * @return <code>authentication</code> \u5b57\u6bb5
         */
        private Optional<BearerSubjectAuthenticator.Authentication> authentication() {
            return authentication;
        }

        /**
         * \u8bfb\u53d6<code>idempotencyScope</code> \u5b57\u6bb5\u3002
         * @return <code>idempotencyScope</code> \u5b57\u6bb5
         */
        private Optional<WebIdempotencyLifecycle.Scope> idempotencyScope() {
            return idempotencyScope;
        }

    }

    /** \u5728\u4e00\u6b21 HTTP \u8bf7\u6c42\u7684\u5904\u7406\u9636\u6bb5\u4e4b\u95f4\u4fdd\u5b58\u5e42\u7b49\u72b6\u6001\u53ca\u6e05\u7406\u8fdb\u5ea6\u3002 */
    private static final class RequestState {

        /** \u5f53\u524d\u8bf7\u6c42\u53d6\u5f97\u7684\u5e42\u7b49\u5904\u7406\u4f5c\u7528\u57df\u3002 */
        private WebIdempotencyLifecycle.Scope idempotencyScope;
        /** \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u94fe\u662f\u5426\u5df2\u7ecf\u62a5\u544a\u5931\u8d25\u3002 */
        private boolean failed;
        /** \u6807\u8bb0\u5f53\u524d\u8bf7\u6c42\u7684\u5e42\u7b49\u4f5c\u7528\u57df\u662f\u5426\u5df2\u7ecf\u5173\u95ed\u3002 */
        private boolean idempotencyClosed;

        /**
         * \u8bbe\u7f6e<code>idempotencyScope</code> \u5b57\u6bb5\u3002
         * @param scope \u8981\u5199\u5165\u7684\u503c
         */
        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        /** \u6807\u8bb0\u5f53\u524d\u8bf7\u6c42\u4e0d\u80fd\u4ee5\u6210\u529f\u72b6\u6001\u5b8c\u6210\u5e42\u7b49\u5904\u7406\u3002 */
        private void failed() {
            failed = true;
        }

        /**
         * \u6309\u8bf7\u6c42\u6700\u7ec8\u72b6\u6001\u5173\u95ed\u5e42\u7b49\u53ca\u5176\u4ed6\u8bf7\u6c42\u4f5c\u7528\u57df\u3002
         * @param successful \u8bf7\u6c42\u662f\u5426\u6309\u6b63\u5e38\u4e1a\u52a1\u7ed3\u679c\u5b8c\u6210
         */
        private void close(boolean successful) {
            if (idempotencyClosed) {
                return;
            }
            try {
                if (Objects.nonNull(idempotencyScope) && successful) {
                    idempotencyScope.complete();
                }
            } finally {
                if (Objects.nonNull(idempotencyScope)) {
                    idempotencyScope.close();
                }
                idempotencyClosed = true;
            }
        }

    }
}
