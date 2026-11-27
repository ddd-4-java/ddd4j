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
import java.util.Optional;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebContextScope;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.webmvc.interceptor.ApiVersionWebInterceptor;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
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

    /** \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5c5e\u6027\u952e\uff0c\u4f9b\u4e0b\u6e38\u5904\u7406\u5668\u8bfb\u53d6\u3002 */
    public static final String CONTEXT_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".context";
    /** \u4fdd\u5b58\u5f53\u524d\u8bf7\u6c42\u4f5c\u7528\u57df\u53ca\u6e05\u7406\u8fdb\u5ea6\u7684\u5c5e\u6027\u952e\u3002 */
    private static final String STATE_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".state";
    /** \u5b58\u653e Spring MVC \u670d\u52a1\u7aef\u8ddf\u8e2a Span \u7684\u8bf7\u6c42\u5c5e\u6027\u540d\u3002 */
    private static final String OTEL_SPAN_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".otelSpan";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;

    /**
     * \u521b\u5efa Ddd4jWebMvcInterceptor \u5b9e\u4f8b\u3002
     * @param authenticator Bearer \u8ba4\u8bc1\u5668
     */
    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator, WebAccessPolicy.required()), null);
    }

    /**
     * \u521b\u5efa Ddd4jWebMvcInterceptor \u5b9e\u4f8b\u3002
     * @param authenticator Bearer \u8ba4\u8bc1\u5668
     * @param publicPath \u516c\u5f00\u8def\u5f84\u5224\u65ad\u5668
     */
    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(publicPath)), null);
    }

    /**
     * \u521b\u5efa Ddd4jWebMvcInterceptor \u5b9e\u4f8b\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param idempotencyLifecycle \u65b9\u6cd5\u53c2\u6570
     */
    public Ddd4jWebMvcInterceptor(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                                  WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
    }

    /**
     * \u6536\u96c6 HTTP \u8bf7\u6c42\u4e2d\u53ef\u7528\u7684\u5934\u540d\u79f0\u53ca\u5176\u503c\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
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
     * preHandle() 方法。
     * @param request 请求对象。
     * @param response 响应对象。
     * @param handler 处理器。
     * @return 判断结果。
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

    /**
     * afterCompletion() 方法。
     * @param request 请求对象。
     * @param response 响应对象。
     * @param handler 处理器。
     * @param exception exception 参数。
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
     * \u4ece\u6846\u67b6\u8bf7\u6c42\u63d0\u53d6\u79df\u6237\u3001\u8ddf\u8e2a\u6807\u8bc6\u3001\u8ba4\u8bc1\u548c\u8def\u7531\u4fe1\u606f\uff0c\u7ec4\u88c5\u5171\u7528\u4e0a\u4e0b\u6587\u3002
     * @param request \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u4f9b\u4e0a\u4e0b\u6587\u548c\u8ba4\u8bc1\u5904\u7406\u94fe\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
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
     * \u6839\u636e\u5f53\u524d Web \u8bf7\u6c42\u89e3\u6790\u9700\u8981\u4f20\u9012\u7684 API \u7248\u672c\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @return \u89e3\u6790\u5230\u7684 API \u7248\u672c\uff0c\u7f3a\u5c11\u65f6\u4fdd\u7559\u7f3a\u7701\u884c\u4e3a
     */
    private ApiVersion resolveApiVersion(HttpServletRequest request) {
        Object attribute = request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE);
        return attribute instanceof ApiVersion ? (ApiVersion) attribute : null;
    }

    /**
     * 类型说明。
     * @author ddd4j
     * @since 3.0.x
     */
    private static final class RequestState {

        /** \u5f53\u524d\u8bf7\u6c42\u83b7\u5f97\u7684 DDD4J \u7ebf\u7a0b\u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u3002 */
        private final WebContextScope contextScope;
        /** \u5f53\u524d\u8bf7\u6c42\u53d6\u5f97\u7684\u5e42\u7b49\u5904\u7406\u4f5c\u7528\u57df\u3002 */
        private WebIdempotencyLifecycle.Scope idempotencyScope;

        /**
         * \u4fdd\u5b58\u5f53\u524d\u8bf7\u6c42\u5f00\u59cb\u65f6\u53d6\u5f97\u7684\u4e0a\u4e0b\u6587\u548c\u8ddf\u8e2a\u8d44\u6e90\u3002
         * @param contextScope \u7ba1\u7406\u5f53\u524d\u7ebf\u7a0b\u8bf7\u6c42\u8d44\u6e90\u7684\u4f5c\u7528\u57df
         */
        private RequestState(WebContextScope contextScope) {
            this.contextScope = contextScope;
        }

        /**
         * \u8bbe\u7f6e<code>idempotencyScope</code> \u5b57\u6bb5\u3002
         * @param scope \u8981\u5199\u5165\u7684\u503c
         */
        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        /**
         * \u6309\u8bf7\u6c42\u6700\u7ec8\u72b6\u6001\u5173\u95ed\u5e42\u7b49\u53ca\u5176\u4ed6\u8bf7\u6c42\u4f5c\u7528\u57df\u3002
         * @param successful \u8bf7\u6c42\u662f\u5426\u6309\u6b63\u5e38\u4e1a\u52a1\u7ed3\u679c\u5b8c\u6210
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
