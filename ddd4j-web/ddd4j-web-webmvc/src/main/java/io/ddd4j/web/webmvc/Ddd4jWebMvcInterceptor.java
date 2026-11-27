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
package io.ddd4j.web.webmvc;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.webmvc.interceptor.ApiVersionWebInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.*;
import java.util.function.Predicate;

/**
 * Spring WebMVC 的统一请求上下文、Bearer Subject 与幂等生命周期拦截器。
 *
 * <p>同时集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用
 * WebOtelIntegration（OTel 集成可选，无依赖时不生效）。
 */
public final class Ddd4jWebMvcInterceptor implements HandlerInterceptor {

    /**
     * 请求上下文属性键，供下游处理器读取。
     */
    public static final String CONTEXT_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".context";

    /**
     * 保存当前请求作用域及清理进度的属性键。
     */
    private static final String STATE_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".state";
    /**
     * 存放 Spring MVC 服务端跟踪 Span 的请求属性名。
     */
    private static final String OTEL_SPAN_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".otelSpan";

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
     * 创建 Ddd4jWebMvcInterceptor 实例。
     *
     * @param authenticator Bearer 认证器
     */
    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator, WebAccessPolicy.required()), null);
    }

    /**
     * 创建 Ddd4jWebMvcInterceptor 实例。
     *
     * @param authenticator Bearer 认证器
     * @param publicPath    公开路径判断器
     */
    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(publicPath)), null);
    }

    /**
     * 创建 Ddd4jWebMvcInterceptor 实例。
     *
     * @param contextFactory       请求上下文工厂
     * @param requestLifecycle     请求生命周期
     * @param idempotencyLifecycle 方法参数
     */
    public Ddd4jWebMvcInterceptor(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                                  WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
    }

    /**
     * 收集 HTTP 请求中可用的头名称及其值。
     *
     * @param request 当前框架 HTTP 请求或交换上下文
     * @return 供请求上下文读取的请求头映射
     */
    private static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (Objects.nonNull(names)) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                String value = request.getHeader(name);
                if (Objects.nonNull(value)) {
                    headers.put(name, value);
                }
            }
        }
        return headers;
    }

    /**
     * 开启 MVC 请求跟踪并绑定请求、认证及幂等上下文。
     *
     * @param request  当前进入调用链的 HTTP 请求
     * @param response 当前准备写出的 HTTP 响应
     * @param handler  MVC 当前匹配的请求处理器
     * @return 当前请求可以继续执行时返回 true
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Map<String, String> headers = extractHeaders(request);
        Object span = WebOtelSupport.startServerSpan(request.getMethod(), request.getRequestURI(), headers);
        request.setAttribute(OTEL_SPAN_ATTRIBUTE, span);
        // 激活 span 为当前 Context
        AutoCloseable scope = WebOtelSupport.activate(span);
        try {
            // 存储 scope 到 request attribute 以便 afterCompletion 关闭
            request.setAttribute(OTEL_SPAN_ATTRIBUTE + ".scope", scope);
        } catch (Throwable ignored) {
        }

        WebRequestContext requestContext = createContext(request);
        request.setAttribute(CONTEXT_ATTRIBUTE, requestContext);
        RequestState state = new RequestState(WebContextScope.open(requestContext));
        request.setAttribute(STATE_ATTRIBUTE, state);
        response.setHeader(WebHeaders.REQUEST_ID, requestContext.requestId());
        response.setHeader(WebHeaders.TRACE_ID, requestContext.traceId());
        try {
            requestLifecycle.authenticate(requestContext)
                    .ifPresent(authentication -> ThreadContext.bind(authentication.subject()));
            idempotencyLifecycle.flatMap(lifecycle -> lifecycle.open(requestContext,
                    request.getHeader(WebHeaders.IDEMPOTENCY_KEY))).ifPresent(state::idempotencyScope);
            return true;
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            state.close(false);
            request.removeAttribute(STATE_ATTRIBUTE);
            throw exception;
        }
    }

    /**
     * 以最终状态关闭 MVC 请求所持有的跟踪和幂等作用域。
     *
     * @param request   当前进入调用链的 HTTP 请求
     * @param response  当前准备写出的 HTTP 响应
     * @param handler   MVC 当前匹配的请求处理器
     * @param exception 当前请求处理时抛出的异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        Object attribute = request.getAttribute(STATE_ATTRIBUTE);
        boolean successful = Objects.isNull(exception) && response.getStatus() < 400;
        if (attribute instanceof RequestState state) {
            state.close(successful);
            request.removeAttribute(STATE_ATTRIBUTE);
        }
        // OTel: 结束 span
        Object span = request.getAttribute(OTEL_SPAN_ATTRIBUTE);
        if (Objects.nonNull(span)) {
            if (Objects.nonNull(exception)) {
                WebOtelSupport.recordError(span, exception);
            }
            WebOtelSupport.endServerSpan(span, response.getStatus());
            request.removeAttribute(OTEL_SPAN_ATTRIBUTE);
        }
        // OTel: 关闭 scope
        Object scope = request.getAttribute(OTEL_SPAN_ATTRIBUTE + ".scope");
        if (scope instanceof AutoCloseable) {
            try {
                ((AutoCloseable) scope).close();
            } catch (Throwable ignored) {
            }
            request.removeAttribute(OTEL_SPAN_ATTRIBUTE + ".scope");
        }
    }

    /**
     * 从框架请求提取租户、跟踪标识、认证和路由信息，组装共用上下文。
     *
     * @param request 当前框架 HTTP 请求
     * @return 供上下文和认证处理链使用的请求上下文
     */
    private WebRequestContext createContext(HttpServletRequest request) {
        return contextFactory.create(new WebRequestData(
                request.getHeader(WebHeaders.REQUEST_ID),
                request.getHeader(WebHeaders.TRACE_ID),
                request.getHeader(WebHeaders.TENANT_ID),
                request.getHeader(WebHeaders.AUTHORIZATION),
                request.getLocale(),
                request.getHeader(WebHeaders.FORWARDED_FOR),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr(),
                request.getMethod(),
                request.getRequestURI()), resolveApiVersion(request));
    }

    /**
     * 根据当前 Web 请求解析需要传递的 API 版本。
     *
     * @param request 当前进入调用链的 HTTP 请求
     * @return 解析到的 API 版本，缺少时保留缺省行为
     */
    private ApiVersion resolveApiVersion(HttpServletRequest request) {
        Object attribute = request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE);
        return attribute instanceof ApiVersion ? (ApiVersion) attribute : null;
    }

    /**
     * 跟踪 MVC 请求上下文及其幂等作用域，以便请求完成时释放。
     */
    private static final class RequestState {

        /**
         * 当前请求获得的 DDD4J 线程上下文作用域。
         */
        private final WebContextScope contextScope;
        /**
         * 当前请求取得的幂等处理作用域。
         */
        private WebIdempotencyLifecycle.Scope idempotencyScope;

        /**
         * 保存当前请求开始时取得的上下文和跟踪资源。
         *
         * @param contextScope 管理当前线程请求资源的作用域
         */
        private RequestState(WebContextScope contextScope) {
            this.contextScope = contextScope;
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
                contextScope.close();
            }
        }
    }
}
