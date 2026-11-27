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
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 HTTP 幂等键映射到框架无关的幂等状态机。
 */
public final class WebIdempotencyLifecycle {

    /**
     * 默认幂等租约 TTL（5 分钟）。
     */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /** \u83b7\u53d6\u3001\u5b8c\u6210\u53ca\u91ca\u653e\u5e42\u7b49\u79df\u7ea6\u7684\u7ec4\u4ef6\u3002 */
    private final IdempotencyGuard guard;
    /** \u5e42\u7b49\u8bb0\u5f55\u4fdd\u7559\u7684\u65f6\u957f\u3002 */
    private final Duration ttl;

    /** 当前应用声明的业务幂等入口，在创建生命周期时装载一次。 */
    private final List<ApplicationIdempotencyPolicy> applicationPolicies = new ArrayList<>();

    /**
     * 使用默认 TTL 构造生命周期。
     *
     * @param guard 幂等守卫，不可为 {@code null}
     */
    public WebIdempotencyLifecycle(IdempotencyGuard guard) {
        this(guard, DEFAULT_TTL);
    }

    /**
     * 使用指定 TTL 构造生命周期。
     *
     * @param guard 幂等守卫，不可为 {@code null}
     * @param ttl   租约存活时长，必须为正数
     * @throws IllegalArgumentException TTL 为零或负数时抛出
     */
    public WebIdempotencyLifecycle(IdempotencyGuard guard, Duration ttl) {
        this.guard = Objects.requireNonNull(guard, "guard must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        ServiceLoader.load(ApplicationIdempotencyPolicy.class).forEach(applicationPolicies::add);
    }

    /**
     * 以幂等键申请租约并开启一次幂等作用域。
     *
     * <p>幂等键为空白时视为非幂等请求，直接返回 {@link Optional#empty()}；
     * 租约被占用（重复请求）时抛出 409 冲突异常。
     *
     * <p>匹配 {@link ApplicationIdempotencyPolicy} 的请求由业务事务处理重试，直接返回空作用域。
     *
     * @param context        Web 请求上下文
     * @param idempotencyKey 客户端提供的幂等键
     * @return 开启的作用域；幂等键为空白时为空
     * @throws WebStatusException 重复的幂等请求时抛出
     */
    public Optional<Scope> open(WebRequestContext context, String idempotencyKey) {
        if (StrKit.isBlank(idempotencyKey)) {
            return Optional.empty();
        }
        WebRequestContext requestContext = Objects.requireNonNull(context, "context must not be null");
        for (ApplicationIdempotencyPolicy policy : applicationPolicies) {
            if (policy.handles(requestContext)) {
                return Optional.empty();
            }
        }
        String storageKey = storageKey(requestContext, idempotencyKey.trim());
        Optional<IdempotencyLease> lease = guard.acquireLease(storageKey, ttl);
        if (!lease.isPresent()) {
            throw new WebStatusException(DefaultWebExceptionTranslator.CONFLICT,
                    "Duplicate idempotent request");
        }
        return Optional.of(new Scope(guard, lease.get()));
    }

    /**
     * \u4ece\u8bf7\u6c42\u8303\u56f4\u4e0e\u5ba2\u6237\u7aef\u5e42\u7b49\u952e\u6784\u9020\u5b58\u50a8\u952e\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param idempotencyKey \u7531\u5ba2\u6237\u7aef\u4f20\u5165\u7684\u5e42\u7b49\u952e\u5934\u503c
     * @return \u7528\u4e8e\u9694\u79bb\u4e0d\u540c\u8bf7\u6c42\u8303\u56f4\u7684\u5e42\u7b49\u8bb0\u5f55\u952e
     */
    private String storageKey(WebRequestContext context, String idempotencyKey) {
        return value(context.tenantId()) + ':' + value(context.method()) + ':' + value(context.path()) + ':'
                + idempotencyKey;
    }

    /**
     * \u5c06\u53ef\u9009\u8bf7\u6c42\u5b57\u6bb5\u5f52\u4e00\u4e3a\u5b58\u50a8\u952e\u4f7f\u7528\u7684\u503c\u3002
     * @param value \u5f53\u524d\u5f85\u5199\u5165\u6216\u89e3\u6790\u7684\u503c
     * @return \u5f52\u4e00\u5316\u7684\u5b57\u7b26\u4e32
     */
    private String value(String value) {
        return StrKit.isBlank(value) ? "_" : value;
    }

    /**
     * 幂等作用域：持有租约并保证 {@link #close()} 前完成或释放。
     *
     * <p>请求成功时调用 {@link #complete()} 落库完成；失败时由 {@link #close()}
     * 释放租约，让后续重试可以重新执行。
     */
    public static final class Scope implements AutoCloseable {

        /** \u83b7\u53d6\u3001\u5b8c\u6210\u53ca\u91ca\u653e\u5e42\u7b49\u79df\u7ea6\u7684\u7ec4\u4ef6\u3002 */
        private final IdempotencyGuard guard;
        /** \u5f53\u524d\u8bf7\u6c42\u6301\u6709\u7684\u5e42\u7b49\u79df\u7ea6\u3002 */
        private final IdempotencyLease lease;
        /** \u5f53\u524d\u5e42\u7b49\u4f5c\u7528\u57df\u662f\u5426\u5df2\u7ecf\u63d0\u4ea4\u6210\u529f\u72b6\u6001\u3002 */
        private boolean completed;
        /** \u5f53\u524d\u5b9e\u4f8b\u662f\u5426\u5df2\u5173\u95ed\u3002 */
        private boolean closed;

        /**
         * \u8bb0\u4f4f\u6240\u83b7\u5f97\u7684\u5e42\u7b49\u79df\u7ea6\u53ca\u5176\u91ca\u653e\u5165\u53e3\u3002
         * @param guard \u8d1f\u8d23\u7ba1\u7406\u5f53\u524d\u8bf7\u6c42\u5e42\u7b49\u72b6\u6001\u7684\u7ec4\u4ef6
         * @param lease \u5f53\u524d\u8bf7\u6c42\u5df2\u7ecf\u83b7\u5f97\u7684\u4fdd\u62a4\u79df\u7ea6
         */
        private Scope(IdempotencyGuard guard, IdempotencyLease lease) {
            this.guard = guard;
            this.lease = lease;
        }

        /**
         * 标记本次幂等请求执行完成（幂等落库）。
         *
         * <p>作用域已关闭或已完成时为空操作。
         */
        public synchronized void complete() {
            if (closed || completed) {
                return;
            }
            guard.complete(lease);
            completed = true;
        }

        /**
         * 关闭作用域：未完成时释放租约，允许后续重试重新执行。
         *
         * <p>重复关闭为空操作，由 {@link AutoCloseable} 契约保证不抛出异常。
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
