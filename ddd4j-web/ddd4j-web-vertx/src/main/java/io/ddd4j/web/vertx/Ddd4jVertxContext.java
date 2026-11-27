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

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Vert.x 请求状态访问与阻塞领域用例桥接。
 */
@UtilityClass
public class Ddd4jVertxContext {

    /** \u5728\u6846\u67b6\u8bf7\u6c42\u4e2d\u4fdd\u5b58 DDD4J \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u952e\u3002 */
    private static final String REQUEST_CONTEXT_KEY = Ddd4jVertxContext.class.getName() + ".request";
    /** \u5728\u6846\u67b6\u8bf7\u6c42\u4e2d\u4fdd\u5b58\u8ba4\u8bc1\u4e3b\u4f53\u7684\u952e\u3002 */
    private static final String SUBJECT_KEY = Ddd4jVertxContext.class.getName() + ".subject";

    /**
     * 获取路由上下文绑定的 Web 请求状态。
     *
     * @param context Vert.x 路由上下文
     * @return 可选的 Web 请求状态
     */
    public static Optional<WebRequestContext> request(RoutingContext context) {
        return Optional.ofNullable(Objects.requireNonNull(context, "context must not be null")
                .get(REQUEST_CONTEXT_KEY));
    }

    /**
     * 获取路由上下文绑定的主体。
     *
     * @param context Vert.x 路由上下文
     * @return 可选主体
     */
    public static Optional<Subject> subject(RoutingContext context) {
        return Optional.ofNullable(Objects.requireNonNull(context, "context must not be null").get(SUBJECT_KEY));
    }

    /**
     * 将请求状态与主体传播到阻塞任务。
     *
     * @param <T> 任务结果类型
     * @param context Vert.x 路由上下文
     * @param task 待执行任务
     * @return 异步任务结果
     */
    public static <T> Future<T> executeBlocking(RoutingContext context, Callable<T> task) {
        RoutingContext routingContext = Objects.requireNonNull(context, "context must not be null");
        Callable<T> actualTask = Objects.requireNonNull(task, "task must not be null");
        WebRequestContext requestContext = request(routingContext)
                .orElseThrow(() -> new IllegalStateException("ddd4j request context is unavailable"));
        Optional<Subject> currentSubject = subject(routingContext);
        return routingContext.vertx().executeBlocking(() -> {
            try (WebContextScope ignored = WebContextScope.open(requestContext)) {
                currentSubject.ifPresent(ThreadContext::bind);
                return actualTask.call();
            }
        });
    }

    /**
     * \u5c06\u89e3\u6790\u540e\u7684 DDD4J \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7ed1\u5b9a\u5230\u5f53\u524d Vert.x \u8def\u7531\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param requestContext \u5f53\u524d HTTP \u8bf7\u6c42\u5df2\u7ecf\u89e3\u6790\u7684 DDD4J \u4e0a\u4e0b\u6587
     */
    static void bindRequest(RoutingContext context, WebRequestContext requestContext) {
        context.put(REQUEST_CONTEXT_KEY, requestContext);
    }

    /**
     * \u5c06\u8ba4\u8bc1\u4e3b\u4f53\u7ed1\u5b9a\u5230\u5f53\u524d Vert.x \u8def\u7531\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param subject \u5f53\u524d\u8bf7\u6c42\u8ba4\u8bc1\u540e\u7ed1\u5b9a\u7684\u4e3b\u4f53\u7ec4\u4ef6
     */
    static void bindSubject(RoutingContext context, Subject subject) {
        context.put(SUBJECT_KEY, subject);
    }
}
