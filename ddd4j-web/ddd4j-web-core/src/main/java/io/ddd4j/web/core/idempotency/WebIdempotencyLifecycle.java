package io.ddd4j.web.core.idempotency;

import io.ddd4j.kit.lang.StrKit;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebStatusException;

/**
 * 将 HTTP 幂等键映射到框架无关的幂等状态机。
 */
public final class WebIdempotencyLifecycle {

    /** \u9ed8\u8ba4\u5e42\u7b49\u79df\u7ea6 TTL\uff085 \u5206\u949f\uff09\u3002 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /** \u83b7\u53d6\u3001\u5b8c\u6210\u53ca\u91ca\u653e\u5e42\u7b49\u79df\u7ea6\u7684\u7ec4\u4ef6\u3002 */
    private final IdempotencyGuard guard;
    /** \u5e42\u7b49\u8bb0\u5f55\u4fdd\u7559\u7684\u65f6\u957f\u3002 */
    private final Duration ttl;

    /**
     * \u4f7f\u7528\u9ed8\u8ba4 TTL \u6784\u9020\u751f\u547d\u5468\u671f\u3002
     * @param guard \u5e42\u7b49\u5b88\u536b\uff0c\u4e0d\u53ef\u4e3a {@code null}
     */
    public WebIdempotencyLifecycle(IdempotencyGuard guard) {
        this(guard, DEFAULT_TTL);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a TTL \u6784\u9020\u751f\u547d\u5468\u671f\u3002
     * @param guard \u5e42\u7b49\u5b88\u536b\uff0c\u4e0d\u53ef\u4e3a {@code null}
     * @param ttl \u79df\u7ea6\u5b58\u6d3b\u65f6\u957f\uff0c\u5fc5\u987b\u4e3a\u6b63\u6570
     * @throws IllegalArgumentException TTL \u4e3a\u96f6\u6216\u8d1f\u6570\u65f6\u629b\u51fa
     */
    public WebIdempotencyLifecycle(IdempotencyGuard guard, Duration ttl) {
        this.guard = Objects.requireNonNull(guard, "guard must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    /**
     * \u4ee5\u5e42\u7b49\u952e\u7533\u8bf7\u79df\u7ea6\u5e76\u5f00\u542f\u4e00\u6b21\u5e42\u7b49\u4f5c\u7528\u57df\u3002
     *
     *  <p>\u5e42\u7b49\u952e\u4e3a\u7a7a\u767d\u65f6\u89c6\u4e3a\u975e\u5e42\u7b49\u8bf7\u6c42\uff0c\u76f4\u63a5\u8fd4\u56de {@link Optional#empty()}\uff1b
     *  \u79df\u7ea6\u88ab\u5360\u7528\uff08\u91cd\u590d\u8bf7\u6c42\uff09\u65f6\u629b\u51fa 409 \u51b2\u7a81\u5f02\u5e38\u3002
     * @param context Web \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param idempotencyKey \u5ba2\u6237\u7aef\u63d0\u4f9b\u7684\u5e42\u7b49\u952e
     * @return \u5f00\u542f\u7684\u4f5c\u7528\u57df\uff1b\u5e42\u7b49\u952e\u4e3a\u7a7a\u767d\u65f6\u4e3a\u7a7a
     * @throws WebStatusException \u91cd\u590d\u7684\u5e42\u7b49\u8bf7\u6c42\u65f6\u629b\u51fa
     */
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
     * \u5e42\u7b49\u4f5c\u7528\u57df\uff1a\u6301\u6709\u79df\u7ea6\u5e76\u4fdd\u8bc1 {@link #close()} \u524d\u5b8c\u6210\u6216\u91ca\u653e\u3002
     *
     *  <p>\u8bf7\u6c42\u6210\u529f\u65f6\u8c03\u7528 {@link #complete()} \u843d\u5e93\u5b8c\u6210\uff1b\u5931\u8d25\u65f6\u7531 {@link #close()}
     *  \u91ca\u653e\u79df\u7ea6\uff0c\u8ba9\u540e\u7eed\u91cd\u8bd5\u53ef\u4ee5\u91cd\u65b0\u6267\u884c\u3002
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
         * \u6807\u8bb0\u672c\u6b21\u5e42\u7b49\u8bf7\u6c42\u6267\u884c\u5b8c\u6210\uff08\u5e42\u7b49\u843d\u5e93\uff09\u3002
         *
         *  <p>\u4f5c\u7528\u57df\u5df2\u5173\u95ed\u6216\u5df2\u5b8c\u6210\u65f6\u4e3a\u7a7a\u64cd\u4f5c\u3002
         */
        public void complete() {
            if (closed || completed) {
                return;
            }
            guard.complete(lease);
            completed = true;
        }

        /**
         * \u5173\u95ed\u4f5c\u7528\u57df\uff1a\u672a\u5b8c\u6210\u65f6\u91ca\u653e\u79df\u7ea6\uff0c\u5141\u8bb8\u540e\u7eed\u91cd\u8bd5\u91cd\u65b0\u6267\u884c\u3002
         *
         *  <p>\u91cd\u590d\u5173\u95ed\u4e3a\u7a7a\u64cd\u4f5c\uff0c\u7531 {@link AutoCloseable} \u5951\u7ea6\u4fdd\u8bc1\u4e0d\u629b\u51fa\u5f02\u5e38\u3002
         */
        @Override
        public void close() {
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
