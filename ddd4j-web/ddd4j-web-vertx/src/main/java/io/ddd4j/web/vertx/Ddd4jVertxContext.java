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

    private static final String REQUEST_CONTEXT_KEY = Ddd4jVertxContext.class.getName() + ".request";
    private static final String SUBJECT_KEY = Ddd4jVertxContext.class.getName() + ".subject";

    public static Optional<WebRequestContext> request(RoutingContext context) {
        return Optional.ofNullable(Objects.requireNonNull(context, "context must not be null")
                .get(REQUEST_CONTEXT_KEY));
    }

    public static Optional<Subject> subject(RoutingContext context) {
        return Optional.ofNullable(Objects.requireNonNull(context, "context must not be null").get(SUBJECT_KEY));
    }

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

    static void bindRequest(RoutingContext context, WebRequestContext requestContext) {
        context.put(REQUEST_CONTEXT_KEY, requestContext);
    }

    static void bindSubject(RoutingContext context, Subject subject) {
        context.put(SUBJECT_KEY, subject);
    }
}
