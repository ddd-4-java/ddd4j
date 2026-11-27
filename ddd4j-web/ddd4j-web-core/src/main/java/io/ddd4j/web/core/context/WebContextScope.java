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
package io.ddd4j.web.core.context;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;

/**
 * 请求上下文作用域，关闭时恢复 ThreadContext 与 MDC 的进入前状态。
 */
public final class WebContextScope implements AutoCloseable {

    /** 请求标识在 ThreadContext 与 MDC 中的键名。 */
    public static final String REQUEST_ID = "request-id";
    /** 链路追踪标识在 ThreadContext 与 MDC 中的键名。 */
    public static final String TRACE_ID = "trace-id";
    /** 客户端 IP 在 ThreadContext 中的键名。 */
    public static final String CLIENT_IP = "client-ip";
    /** HTTP 请求方法在 ThreadContext 中的键名。 */
    public static final String HTTP_METHOD = "http-method";
    /** HTTP 请求路径在 ThreadContext 中的键名。 */
    public static final String HTTP_PATH = "http-path";

    /** \u7ba1\u7406\u5f53\u524d\u8bf7\u6c42\u7ed1\u5b9a\u7684\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u53ca\u5176\u6062\u590d\u52a8\u4f5c\u3002 */
    private final ThreadContext.Scope threadScope;
    /** \u8fdb\u5165\u8bf7\u6c42\u4f5c\u7528\u57df\u524d\u4fdd\u5b58\u7684 SLF4J MDC \u503c\u3002 */
    private final Map<String, String> previousMdc;
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
    private boolean closed;

    /**
     * \u4fdd\u5b58\u539f\u7ebf\u7a0b\u72b6\u6001\u5e76\u4e3a\u5f53\u524d\u8bf7\u6c42\u5f00\u542f\u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private WebContextScope(WebRequestContext context) {
        WebRequestContext requestContext = Objects.requireNonNull(context, "context must not be null");
        this.threadScope = ThreadContext.open();
        this.previousMdc = MDC.getCopyOfContextMap();
        bind(requestContext);
    }

    /**
     * 打开资源。
     * @param context JUnit 扩展上下文。
     * @return 当前对象自身，支持链式调用。
     */
    public static WebContextScope open(WebRequestContext context) {
        return new WebContextScope(context);
    }

    /**
     * 关闭并释放资源。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        threadScope.close();
        if (Objects.isNull(previousMdc)) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousMdc);
        }
        closed = true;
    }

    /**
     * \u5c06\u8bf7\u6c42\u8eab\u4efd\u3001\u8ddf\u8e2a\u4fe1\u606f\u53ca\u7f51\u7edc\u5143\u6570\u636e\u7ed1\u5b9a\u5230\u5f53\u524d\u7ebf\u7a0b\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void bind(WebRequestContext context) {
        put(REQUEST_ID, context.requestId());
        put(TRACE_ID, context.traceId());
        put(ContextConstants.TENANT_ID, context.tenantId());
        put(ContextConstants.AUTHORIZATION, context.authorization());
        put(ContextConstants.LOCALE, context.locale());
        put(CLIENT_IP, context.clientIp());
        put(HTTP_METHOD, context.method());
        put(HTTP_PATH, context.path());
        putMdc(REQUEST_ID, context.requestId());
        putMdc(TRACE_ID, context.traceId());
        putMdc(ContextConstants.TENANT_ID, context.tenantId());
    }

    /**
     * \u6709\u503c\u65f6\u5411\u5f53\u524d DDD4J \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5199\u5165\u4e00\u4e2a\u5c5e\u6027\u3002
     * @param key \u5f53\u524d\u5199\u5165\u7684\u4e0a\u4e0b\u6587\u952e\u3001\u5e42\u7b49\u952e\u6216\u5c5e\u6027\u540d
     * @param value \u5f53\u524d\u5f85\u5199\u5165\u6216\u89e3\u6790\u7684\u503c
     */
    private void put(String key, Object value) {
        if (Objects.nonNull(value)) {
            ThreadContext.put(key, value);
        }
    }

    /**
     * \u5411\u5f53\u524d\u65e5\u5fd7 MDC \u5199\u5165\u8bf7\u6c42\u5173\u8054\u5c5e\u6027\u3002
     * @param key \u5f53\u524d\u5199\u5165\u7684\u4e0a\u4e0b\u6587\u952e\u3001\u5e42\u7b49\u952e\u6216\u5c5e\u6027\u540d
     * @param value \u5f53\u524d\u5f85\u5199\u5165\u6216\u89e3\u6790\u7684\u503c
     */
    private void putMdc(String key, String value) {
        if (StrKit.isNotBlank(value)) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }
}
