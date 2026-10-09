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
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.webmvc.interceptor.ApiVersionWebInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Spring WebMVC 的统一请求上下文、Bearer Subject 与幂等生命周期拦截器。
 *
 * <p>同时集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用
 * WebOtelIntegration（OTel 集成可选，无依赖时不生效）。
 */
public final class Ddd4jWebMvcInterceptor implements HandlerInterceptor {

    private static final String STATE_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".state";
    private static final String OTEL_SPAN_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".otelSpan";

    /**
     * 当前请求的 {@link WebRequestContext} 在请求属性中的挂载键。
     * 业务处理器经 {@code request.getAttribute(CONTEXT_ATTRIBUTE)} 可读取完整上下文
     * （如解析出的 API 版本）；未启用版本解析时上下文的 {@code apiVersion} 为 null。
     */
    public static final String CONTEXT_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".context";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;

    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator, WebAccessPolicy.required()), null);
    }

    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(publicPath)), null);
    }

    public Ddd4jWebMvcInterceptor(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                                  WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
    }

    /**
     * 请求前置处理：开启追踪 span，创建携带 API 版本的 {@link WebRequestContext}
     * 并绑定到请求属性（{@link #CONTEXT_ATTRIBUTE}）与请求级资源，随后执行认证等生命周期前置。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  目标处理器
     * @return 放行后续处理返回 {@code true}，中断请求返回 {@code false}
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
        RequestState state = new RequestState(WebContextScope.open(requestContext));
        request.setAttribute(STATE_ATTRIBUTE, state);
        request.setAttribute(CONTEXT_ATTRIBUTE, requestContext);
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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        Object attribute = request.getAttribute(STATE_ATTRIBUTE);
        boolean successful = Objects.isNull(exception) && response.getStatus() < 400;
        if (attribute instanceof RequestState) {
            RequestState state = (RequestState) attribute;
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
     * 读取 {@link ApiVersionWebInterceptor} 先行挂载的解析结果。
     * 未启用版本解析（或版本拦截器未注册）时属性缺失，返回 null，行为与接线前一致。
     *
     * @param request 当前请求
     * @return 解析出的 API 版本，可为 null
     */
    private ApiVersion resolveApiVersion(HttpServletRequest request) {
        Object attribute = request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE);
        return attribute instanceof ApiVersion ? (ApiVersion) attribute : null;
    }

    private static final class RequestState {

        private final WebContextScope contextScope;
        private WebIdempotencyLifecycle.Scope idempotencyScope;

        private RequestState(WebContextScope contextScope) {
            this.contextScope = contextScope;
        }

        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

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
