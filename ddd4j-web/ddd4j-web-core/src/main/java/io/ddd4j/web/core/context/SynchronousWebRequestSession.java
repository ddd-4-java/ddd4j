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

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator.Authentication;

import java.util.Objects;
import java.util.Optional;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;

/**
 * 同步 Web 请求的上下文、认证与幂等事务边界。
 *
 * <p>该会话必须在创建它的线程关闭；响应式适配器应使用各自的上下文传播机制。</p>
 */
public final class SynchronousWebRequestSession implements AutoCloseable {

    private final WebRequestContext requestContext;
    private final WebContextScope contextScope;
    private final Optional<WebIdempotencyLifecycle.Scope> idempotencyScope;
    private boolean closed;

    private SynchronousWebRequestSession(WebRequestContext requestContext, WebContextScope contextScope,
                                         Optional<WebIdempotencyLifecycle.Scope> idempotencyScope) {
        this.requestContext = requestContext;
        this.contextScope = contextScope;
        this.idempotencyScope = idempotencyScope;
    }

    public static SynchronousWebRequestSession open(WebRequestContext requestContext,
                                                    WebRequestLifecycle requestLifecycle,
                                                    WebIdempotencyLifecycle idempotencyLifecycle,
                                                    String idempotencyKey) {
        WebRequestContext context = Objects.requireNonNull(requestContext, "requestContext must not be null");
        WebRequestLifecycle lifecycle = Objects.requireNonNull(requestLifecycle,
                "requestLifecycle must not be null");
        WebContextScope contextScope = WebContextScope.open(context);
        try {
            Optional<Authentication> authentication = lifecycle.authenticate(context);
            authentication.ifPresent(result -> ThreadContext.bind(result.subject()));
            Optional<WebIdempotencyLifecycle.Scope> idempotencyScope = Objects.isNull(idempotencyLifecycle)
                    ? Optional.empty() : idempotencyLifecycle.open(context, idempotencyKey);
            return new SynchronousWebRequestSession(context, contextScope, idempotencyScope);
        } catch (RuntimeException exception) {
            contextScope.close();
            throw exception;
        }
    }

    public WebRequestContext requestContext() {
        return requestContext;
    }

    public void complete(boolean successful) {
        if (successful) {
            idempotencyScope.ifPresent(WebIdempotencyLifecycle.Scope::complete);
        }
        close();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            idempotencyScope.ifPresent(WebIdempotencyLifecycle.Scope::close);
        } finally {
            contextScope.close();
            closed = true;
        }
    }
}
