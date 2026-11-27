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
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;

import java.util.Objects;
import java.util.Optional;

/**
 * 同步 Web 请求的上下文、认证与幂等事务边界。
 *
 * <p>该会话必须在创建它的线程关闭；响应式适配器应使用各自的上下文传播机制。</p>
 */
public final class SynchronousWebRequestSession implements AutoCloseable {

    /** \u5f53\u524d HTTP \u8bf7\u6c42\u89e3\u6790\u540e\u7528\u4e8e\u8de8\u5c42\u4f20\u9012\u7684\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContext requestContext;
    /** \u5f53\u524d\u8bf7\u6c42\u83b7\u5f97\u7684 DDD4J \u7ebf\u7a0b\u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u3002 */
    private final WebContextScope contextScope;
    /** \u5f53\u524d\u8bf7\u6c42\u53d6\u5f97\u7684\u5e42\u7b49\u5904\u7406\u4f5c\u7528\u57df\u3002 */
    private final Optional<WebIdempotencyLifecycle.Scope> idempotencyScope;
    /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
    private boolean closed;

    /**
     * \u4fdd\u5b58\u540c\u6b65\u8bf7\u6c42\u7684\u4e0a\u4e0b\u6587\u4ee5\u53ca\u7ed3\u675f\u65f6\u8981\u6e05\u7406\u7684\u76f8\u5173\u4f5c\u7528\u57df\u3002
     * @param requestContext \u5f53\u524d HTTP \u8bf7\u6c42\u5df2\u7ecf\u89e3\u6790\u7684 DDD4J \u4e0a\u4e0b\u6587
     * @param contextScope \u7ba1\u7406\u5f53\u524d\u7ebf\u7a0b\u8bf7\u6c42\u8d44\u6e90\u7684\u4f5c\u7528\u57df
     * @param idempotencyScope \u5f53\u524d\u8bf7\u6c42\u83b7\u53d6\u7684\u53ef\u9009\u5e42\u7b49\u4f5c\u7528\u57df
     */
    private SynchronousWebRequestSession(WebRequestContext requestContext, WebContextScope contextScope,
                                         Optional<WebIdempotencyLifecycle.Scope> idempotencyScope) {
        this.requestContext = requestContext;
        this.contextScope = contextScope;
        this.idempotencyScope = idempotencyScope;
    }

    /**
     * \u6253\u5f00\u540c\u6b65\u8bf7\u6c42\u4f1a\u8bdd\uff0c\u5b8c\u6210\u4e0a\u4e0b\u6587\u7ed1\u5b9a\u3001\u8ba4\u8bc1\u548c\u5e42\u7b49\u79df\u7ea6\u83b7\u53d6\u3002
     * @param requestContext \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param requestLifecycle \u8bf7\u6c42\u8ba4\u8bc1\u751f\u547d\u5468\u671f
     * @param idempotencyLifecycle \u5e42\u7b49\u751f\u547d\u5468\u671f\uff1b\u53ef\u4e3a\u7a7a
     * @param idempotencyKey \u5e42\u7b49\u952e
     * @return \u5df2\u6253\u5f00\u7684\u8bf7\u6c42\u4f1a\u8bdd
     */
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

    /**
     * \u8fd4\u56de\u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002
     * @return \u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    public WebRequestContext requestContext() {
        return requestContext;
    }

    /**
     * \u5b8c\u6210\u8bf7\u6c42\u5904\u7406\u5e76\u5173\u95ed\u4f1a\u8bdd\u3002
     * @param successful \u4e1a\u52a1\u5904\u7406\u662f\u5426\u6210\u529f
     */
    public void complete(boolean successful) {
        try {
            if (successful) {
                idempotencyScope.ifPresent(WebIdempotencyLifecycle.Scope::complete);
            }
        } finally {
            close();
        }
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>contextScope.close()</code>。</p>
     */
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
