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
package io.ddd4j.web.core.idempotency;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebStatusException;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 HTTP 幂等键映射到框架无关的幂等状态机。
 */
public final class WebIdempotencyLifecycle {

    /** 未显式指定时使用的默认幂等键存活时长。 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final IdempotencyGuard guard;
    private final Duration ttl;

    public WebIdempotencyLifecycle(IdempotencyGuard guard) {
        this(guard, DEFAULT_TTL);
    }

    public WebIdempotencyLifecycle(IdempotencyGuard guard, Duration ttl) {
        this.guard = Objects.requireNonNull(guard, "guard must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    public Optional<Scope> open(WebRequestContext context, String idempotencyKey) {
        if (StrKit.isBlank(idempotencyKey)) {
            return Optional.empty();
        }
        WebRequestContext requestContext = Objects.requireNonNull(context, "context must not be null");
        String storageKey = storageKey(requestContext, idempotencyKey.trim());
        Optional<IdempotencyLease> lease = guard.acquireLease(storageKey, ttl);
        if (lease.isEmpty()) {
            throw new WebStatusException(DefaultWebExceptionTranslator.CONFLICT,
                    "Duplicate idempotent request");
        }
        return Optional.of(new Scope(guard, lease.orElseThrow()));
    }

    private String storageKey(WebRequestContext context, String idempotencyKey) {
        return value(context.tenantId()) + ':' + value(context.method()) + ':' + value(context.path()) + ':'
                + idempotencyKey;
    }

    private String value(String value) {
        return StrKit.isBlank(value) ? "_" : value;
    }

    /**
     * 单次幂等请求的作用域：处理期间持有租约，关闭时按完成情况提交或回滚。
     */
    public static final class Scope implements AutoCloseable {

        private final IdempotencyGuard guard;
        private final IdempotencyLease lease;
        private boolean completed;
        private boolean closed;

        private Scope(IdempotencyGuard guard, IdempotencyLease lease) {
            this.guard = guard;
            this.lease = lease;
        }

        /**
         * complete() 方法。
         */
        public synchronized void complete() {
            if (closed || completed) {
                return;
            }
            guard.complete(lease);
            completed = true;
        }

        /**
         * 关闭并释放资源。
         */
        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            if (!completed) {
                guard.release(lease);
            }
            closed = true;
        }
    }
}
