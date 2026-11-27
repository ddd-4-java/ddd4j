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
package io.ddd4j.web.javalin;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import io.ddd4j.kit.lang.StrKit;
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
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 在 Javalin 创建阶段安装统一请求上下文、Bearer Subject、异常与幂等处理链。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Slf4j
public final class Ddd4jJavalinWeb {

    /**
     * 保存当前请求作用域及清理进度的属性键。
     */
    private static final String STATE_ATTRIBUTE = Ddd4jJavalinWeb.class.getName() + ".state";
    /**
     * 在 Javalin 请求属性中保存服务端 Span 的键。
     */
    private static final String OTEL_SPAN_ATTR = Ddd4jJavalinWeb.class.getName() + ".otelSpan";

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
     * 提供依赖就绪状态的 Web 端点。
     */
    private final ReadinessEndpoint readinessEndpoint;

    /**
     * 使用默认请求生命周期和就绪注册表创建 Web 适配器。
     */
    public Ddd4jJavalinWeb() {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, new RuntimeReadinessRegistry());
    }

    /**
     * 使用应用 Runtime 的 registry 注册标准 readiness 端点。
     *
     * @param readinessRegistry 应用 Runtime 管理的就绪状态注册表
     */
    public Ddd4jJavalinWeb(RuntimeReadinessRegistry readinessRegistry) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, readinessRegistry);
    }

    /**
     * 使用指定请求生命周期和异常转换器创建 Web 适配器。
     *
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param exceptionTranslator  异常转换器
     * @param idempotencyLifecycle 幂等请求生命周期
     */
    public Ddd4jJavalinWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                           WebExceptionTranslator exceptionTranslator,
                           WebIdempotencyLifecycle idempotencyLifecycle) {
        this(contextFactory, requestLifecycle, exceptionTranslator, idempotencyLifecycle,
                new RuntimeReadinessRegistry());
    }

    /**
     * 使用指定依赖和就绪注册表创建 Web 适配器。
     *
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param exceptionTranslator  异常转换器
     * @param idempotencyLifecycle 幂等请求生命周期
     * @param readinessRegistry    就绪状态注册表
     */
    public Ddd4jJavalinWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                           WebExceptionTranslator exceptionTranslator,
                           WebIdempotencyLifecycle idempotencyLifecycle,
                           RuntimeReadinessRegistry readinessRegistry) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
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
    private static Map<String, String> extractHeaders(Context context) {
        Map<String, String> headers = new HashMap<>();
        context.headerMap().forEach((k, v) -> {
            if (Objects.nonNull(v)) {
                headers.put(k, v);
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
     * 注册请求上下文、异常处理和就绪端点。
     *
     * @param config Javalin 配置
     */
    public void configure(JavalinConfig config) {
        JavalinConfig javalinConfig = Objects.requireNonNull(config, "config must not be null");
        javalinConfig.routes.before(this::openContext);
        javalinConfig.routes.after(this::completeContext);
        javalinConfig.routes.exception(Exception.class, this::handleException);
        javalinConfig.routes.get(ReadinessEndpoint.PATH, this::readiness);
    }

    /**
     * 将当前运行时就绪状态写入 Javalin 响应。
     *
     * @param context 当前框架调用所携带的请求上下文
     */
    private void readiness(Context context) {
        ReadinessResponse response = readinessEndpoint.readiness();
        context.status(response.httpStatus()).json(response);
    }

    /**
     * 在 Javalin 路由处理前启动跟踪并绑定请求上下文。
     *
     * @param context 当前框架调用所携带的请求上下文
     */
    private void openContext(Context context) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Map<String, String> headers = extractHeaders(context);
        Object span = WebOtelSupport.startServerSpan(
                context.method().name(), context.path(), headers);
        context.attribute(OTEL_SPAN_ATTR, span);
        AutoCloseable otelScope = WebOtelSupport.activate(span);
        RequestState state = null;
        try {
            WebRequestContext requestContext = createContext(context);
            state = new RequestState(WebContextScope.open(requestContext), otelScope);
            context.attribute(STATE_ATTRIBUTE, state);
            context.header(WebHeaders.REQUEST_ID, requestContext.requestId());
            context.header(WebHeaders.TRACE_ID, requestContext.traceId());
            requestLifecycle.authenticate(requestContext)
                    .ifPresent(authentication -> ThreadContext.bind(authentication.subject()));
            RequestState activeState = state;
            idempotencyLifecycle.flatMap(lifecycle -> lifecycle.open(requestContext,
                    context.header(WebHeaders.IDEMPOTENCY_KEY))).ifPresent(activeState::idempotencyScope);
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            if (Objects.nonNull(state)) {
                state.close(false);
                context.attribute(STATE_ATTRIBUTE, null);
            } else {
                closeScope(otelScope);
            }
            throw exception;
        }
    }

    /**
     * 按 Javalin 请求最终结果完成跟踪和上下文清理。
     *
     * @param context 当前框架调用所携带的请求上下文
     */
    private void completeContext(Context context) {
        // OTel: 结束 span
        Object span = context.attribute(OTEL_SPAN_ATTR);
        if (Objects.nonNull(span)) {
            WebOtelSupport.endServerSpan(span, context.statusCode());
        }
        closeContext(context, context.statusCode() < 400);
    }

    /**
     * 将 Javalin 路由异常转换并写入统一的 HTTP 错误响应。
     *
     * @param exception 当前请求处理时抛出的异常
     * @param context   当前框架调用所携带的请求上下文
     */
    private void handleException(Exception exception, Context context) {
        WebError error = exceptionTranslator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Javalin request failure: {} {}", context.method(), context.path(), exception);
        }
        // OTel: 记录异常
        Object span = context.attribute(OTEL_SPAN_ATTR);
        if (Objects.nonNull(span)) {
            WebOtelSupport.recordError(span, exception);
            WebOtelSupport.endServerSpan(span, error.status());
        }
        context.status(error.status()).json(error.toResponse());
        closeContext(context, false);
    }

    /**
     * 从框架请求提取租户、跟踪标识、认证和路由信息，组装共用上下文。
     *
     * @param context 当前框架 HTTP 请求
     * @return 供上下文和认证处理链使用的请求上下文
     */
    private WebRequestContext createContext(Context context) {
        return contextFactory.create(new WebRequestData(
                context.header(WebHeaders.REQUEST_ID),
                context.header(WebHeaders.TRACE_ID),
                context.header(WebHeaders.TENANT_ID),
                context.header(WebHeaders.AUTHORIZATION),
                resolveLocale(context),
                context.header(WebHeaders.FORWARDED_FOR),
                context.header("X-Real-IP"),
                context.req().getRemoteAddr(),
                context.method().name(),
                context.path()));
    }

    /**
     * 关闭并移除该 Javalin 请求已保存的作用域。
     *
     * @param context    当前框架调用所携带的请求上下文
     * @param successful 请求是否按正常业务结果完成
     */
    private void closeContext(Context context, boolean successful) {
        RequestState state = context.attribute(STATE_ATTRIBUTE);
        if (Objects.nonNull(state)) {
            state.close(successful);
            context.attribute(STATE_ATTRIBUTE, null);
        }
    }

    /**
     * 使用请求语言偏好列表中的第一项，未提供偏好时用 JVM 默认区域。
     *
     * @param context 提供语言偏好的请求或请求头
     * @return 本次请求使用的区域
     */
    private Locale resolveLocale(Context context) {
        String language = context.header("Accept-Language");
        return StrKit.isBlank(language) ? Locale.getDefault() : Locale.forLanguageTag(language.split(",", 2)[0]);
    }

    /**
     * 单次请求的处理状态，聚合请求上下文作用域、追踪作用域与幂等作用域并按序关闭。
     */
    private static final class RequestState {

        /**
         * 当前请求获得的 DDD4J 线程上下文作用域。
         */
        private final WebContextScope contextScope;
        /**
         * 当前请求用于激活服务端 Span 的跟踪上下文作用域。
         */
        private final AutoCloseable otelScope;
        /**
         * 当前请求取得的幂等处理作用域。
         */
        private WebIdempotencyLifecycle.Scope idempotencyScope;
        /**
         * 当前实例是否已关闭。
         */
        private boolean closed;

        /**
         * 保存当前请求开始时取得的上下文和跟踪资源。
         *
         * @param contextScope 管理当前线程请求资源的作用域
         * @param otelScope    当前请求的 OpenTelemetry 上下文作用域
         */
        private RequestState(WebContextScope contextScope, AutoCloseable otelScope) {
            this.contextScope = contextScope;
            this.otelScope = otelScope;
        }

        /**
         * 设置<code>idempotencyScope</code> 字段。
         *
         * @param scope 要写入的值
         */
        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        /**
         * 按请求最终状态关闭幂等及其他请求作用域。
         *
         * @param successful 请求是否按正常业务结果完成
         */
        private void close(boolean successful) {
            if (closed) {
                return;
            }
            try {
                if (Objects.nonNull(idempotencyScope)) {
                    try {
                        if (successful) {
                            idempotencyScope.complete();
                        }
                    } finally {
                        idempotencyScope.close();
                    }
                }
            } finally {
                try {
                    contextScope.close();
                } finally {
                    closeScope(otelScope);
                    closed = true;
                }
            }
        }
    }
}
