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

    /**
     * 保存 Vert.x 请求处理状态和清理句柄的键。
     */
    private static final String STATE_KEY = Ddd4jVertxWeb.class.getName() + ".state";
    /**
     * 保存框架请求服务端 Span 的键。
     */
    private static final String OTEL_SPAN_KEY = Ddd4jVertxWeb.class.getName() + ".otelSpan";

    /**
     * 根据框架请求元数据装配共用 Web 请求上下文。
     */
    private final WebRequestContextFactory contextFactory;
    /**
     * 开启和关闭当前 Web 请求上下文的组件。
     */
    private final WebRequestLifecycle requestLifecycle;
    /**
     * 将框架异常映射为统一 WebError 的转换器。
     */
    private final WebExceptionTranslator exceptionTranslator;
    /**
     * 在请求处理阶段获取及释放幂等保护的组件。
     */
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    /**
     * 将 Vert.x 应用结果写成 HTTP JSON 正文的编码器。
     */
    private final Function<Object, String> jsonEncoder;
    /**
     * 提供依赖就绪状态的 Web 端点。
     */
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
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param exceptionTranslator  异常翻译器
     * @param idempotencyLifecycle 幂等生命周期，可为空
     * @param jsonEncoder          JSON 编码器
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
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param exceptionTranslator  异常翻译器
     * @param idempotencyLifecycle 幂等生命周期，可为空
     * @param jsonEncoder          JSON 编码器
     * @param readinessRegistry    就绪注册表
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
     * 收集 HTTP 请求中可用的头名称及其值。
     *
     * @param context 当前框架 HTTP 请求或交换上下文
     * @return 供请求上下文读取的请求头映射
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
     * 激活服务端跟踪上下文后运行当前回调并释放作用域。
     *
     * @param span   当前请求的跟踪 Span
     * @param action 需要在已激活跟踪上下文中执行的操作
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
     * 将当前运行时就绪状态写入 Vert.x 响应。
     *
     * @param context 当前框架调用所携带的请求上下文
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
     * 按路由访问策略解析主体并取得需要的幂等保护。
     *
     * @param routingContext 当前 Vert.x 路由请求上下文
     * @param requestContext 当前 HTTP 请求已经解析的 DDD4J 上下文
     * @return 认证与可用幂等作用域的组合
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
     * 从框架请求提取租户、跟踪标识、认证和路由信息，组装共用上下文。
     *
     * @param context 当前框架 HTTP 请求
     * @return 供上下文和认证处理链使用的请求上下文
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
     * 根据最终响应结束跟踪并清理 Vert.x 请求作用域。
     *
     * @param context 当前框架调用所携带的请求上下文
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

    /**
     * 保存 Vert.x 当前请求的认证结果及已取得的幂等作用域。
     */
    private static final class AuthenticationResult {

        /**
         * 当前请求的认证结果，公开路由可以不携带主体。
         */
        private final Optional<BearerSubjectAuthenticator.Authentication> authentication;

        /**
         * 当前请求取得的幂等处理作用域。
         */
        private final Optional<WebIdempotencyLifecycle.Scope> idempotencyScope;

        /**
         * 将进入路由前取得的认证结果和幂等作用域一起保存。
         *
         * @param authentication   当前请求已取得的认证结果
         * @param idempotencyScope 当前请求获取的可选幂等作用域
         */
        private AuthenticationResult(Optional<BearerSubjectAuthenticator.Authentication> authentication,
                                     Optional<WebIdempotencyLifecycle.Scope> idempotencyScope) {
            this.authentication = authentication;
            this.idempotencyScope = idempotencyScope;
        }

        /**
         * 读取<code>authentication</code> 字段。
         *
         * @return <code>authentication</code> 字段
         */
        private Optional<BearerSubjectAuthenticator.Authentication> authentication() {
            return authentication;
        }

        /**
         * 读取<code>idempotencyScope</code> 字段。
         *
         * @return <code>idempotencyScope</code> 字段
         */
        private Optional<WebIdempotencyLifecycle.Scope> idempotencyScope() {
            return idempotencyScope;
        }

    }

    /**
     * 在一次 HTTP 请求的处理阶段之间保存幂等状态及清理进度。
     */
    private static final class RequestState {

        /**
         * 当前请求取得的幂等处理作用域。
         */
        private WebIdempotencyLifecycle.Scope idempotencyScope;
        /**
         * 当前请求处理链是否已经报告失败。
         */
        private boolean failed;
        /**
         * 标记当前请求的幂等作用域是否已经关闭。
         */
        private boolean idempotencyClosed;

        /**
         * 设置<code>idempotencyScope</code> 字段。
         *
         * @param scope 要写入的值
         */
        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        /**
         * 标记当前请求不能以成功状态完成幂等处理。
         */
        private void failed() {
            failed = true;
        }

        /**
         * 按请求最终状态关闭幂等及其他请求作用域。
         *
         * @param successful 请求是否按正常业务结果完成
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
