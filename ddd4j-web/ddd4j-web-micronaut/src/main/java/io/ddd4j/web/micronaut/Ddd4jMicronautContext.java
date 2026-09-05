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
package io.ddd4j.web.micronaut;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;

import java.util.Objects;
import java.util.Optional;

/**
 * 通过 Micronaut PropagatedContext 传播 ddd4j 请求上下文。
 */public final class Ddd4jMicronautContext {

    private static final ThreadLocal<Ddd4jMicronautContext> CURRENT = new ThreadLocal<>();

    private final WebRequestContext requestContext;
    private final Optional<Subject> subject;

    public Ddd4jMicronautContext(WebRequestContext requestContext, Optional<Subject> subject) {
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext must not be null");
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
    }

    public WebRequestContext requestContext() {
        return requestContext;
    }

    public Optional<Subject> subject() {
        return subject;
    }

    public static Optional<Ddd4jMicronautContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void set(Ddd4jMicronautContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public WebContextScope openContext() {
        WebContextScope scope = WebContextScope.open(requestContext);
        subject.ifPresent(ThreadContext::bind);
        return scope;
    }
}
