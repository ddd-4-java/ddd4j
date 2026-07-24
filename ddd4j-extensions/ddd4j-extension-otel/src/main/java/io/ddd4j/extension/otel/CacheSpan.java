package io.ddd4j.extension.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.function.Supplier;

/**
 * 缓存操作的 OTel Span 辅助工具。
 *
 * <p>为 {@link io.ddd4j.cache.CacheKit} 提供零侵入式 span 包装。
 * 无 OTel 时所有方法为 noop，零开销。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Object value = CacheSpan.instrument("userCache", "redis", "get",
 *     () -> cache.get(key), () -> !cache.contains(key));
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class CacheSpan {

    private CacheSpan() {
    }

    /**
     * 包装缓存读操作。
     *
     * @param biz      业务标识（如 "userCache"）
     * @param backend  后端标识（如 "redis"、"caffeine"）
     * @param supplier 缓存读操作
     * @param missCheck 未命中检查（true 表示 cache miss）
     * @return supplier 的返回值
     */
    public static <T> T instrument(String biz, String backend, Supplier<T> supplier, Supplier<Boolean> missCheck) {
        if (!Ddd4jOtel.isAvailable()) {
            return supplier.get();
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.cache.get")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(Ddd4jOtel.ATTR_CACHE_BIZ, biz)
                .setAttribute(Ddd4jOtel.ATTR_CACHE_BACKEND, backend)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            T result = supplier.get();
            boolean hit = missCheck != null && !missCheck.get();
            span.setAttribute(Ddd4jOtel.ATTR_CACHE_HIT, hit);
            return result;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * 包装缓存写操作。
     *
     * @param biz      业务标识
     * @param backend  后端标识
     * @param op       操作类型（put/evict）
     * @param runnable 写操作
     */
    public static void instrument(String biz, String backend, String op, Runnable runnable) {
        if (!Ddd4jOtel.isAvailable()) {
            runnable.run();
            return;
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.cache." + op)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(Ddd4jOtel.ATTR_CACHE_BIZ, biz)
                .setAttribute(Ddd4jOtel.ATTR_CACHE_BACKEND, backend)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            runnable.run();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }
}