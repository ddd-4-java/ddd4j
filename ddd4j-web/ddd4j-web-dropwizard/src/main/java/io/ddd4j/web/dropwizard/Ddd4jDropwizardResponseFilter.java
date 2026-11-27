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
package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.observability.WebOtelSupport;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

import java.util.Objects;

/**
 * 回传请求标识，并按响应状态提交或释放幂等请求会话。
 */
public final class Ddd4jDropwizardResponseFilter implements ContainerResponseFilter {

    /**
     * 尝试关闭请求作用域，清理故障不打断响应处理。
     *
     * @param scope 需要在当前请求结束后关闭的作用域
     */
    private static void closeScope(Object scope) {
        if (scope instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 过滤数据。
     *
     * @param request  请求对象。
     * @param response 响应对象。
     */
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object span = request.getProperty(Ddd4jDropwizardRequestFilter.OTEL_SPAN_PROPERTY);
        Object scope = request.getProperty(Ddd4jDropwizardRequestFilter.OTEL_SCOPE_PROPERTY);
        Object sessionValue = request.getProperty(Ddd4jDropwizardRequestFilter.SESSION_PROPERTY);
        int status = 500;
        try (SynchronousWebRequestSession session = sessionValue instanceof SynchronousWebRequestSession
                ? (SynchronousWebRequestSession) sessionValue : null) {
            Object contextValue = request.getProperty(Ddd4jDropwizardRequestFilter.CONTEXT_PROPERTY);
            if (contextValue instanceof WebRequestContext context) {
                response.getHeaders().putSingle(WebHeaders.REQUEST_ID, context.requestId());
                response.getHeaders().putSingle(WebHeaders.TRACE_ID, context.traceId());
            }
            status = response.getStatus();
            if (Objects.nonNull(session)) {
                session.complete(status < 400);
            }
        } finally {
            request.removeProperty(Ddd4jDropwizardRequestFilter.CONTEXT_PROPERTY);
            request.removeProperty(Ddd4jDropwizardRequestFilter.SESSION_PROPERTY);
            if (Objects.nonNull(span)) {
                WebOtelSupport.endServerSpan(span, status);
            }
            closeScope(scope);
            request.removeProperty(Ddd4jDropwizardRequestFilter.OTEL_SPAN_PROPERTY);
            request.removeProperty(Ddd4jDropwizardRequestFilter.OTEL_SCOPE_PROPERTY);
        }
    }
}
