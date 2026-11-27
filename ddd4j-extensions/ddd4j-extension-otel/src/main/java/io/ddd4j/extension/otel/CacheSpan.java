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
package io.ddd4j.extension.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 缓存操作的 OTel Span 辅助工具。
 *
 * <p>为 {@code CacheKit} 提供零侵入式 span 包装。
 * 无 OTel 时所有方法为 noop，零开销。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Object value = CacheSpan.instrument("userCache", "redis", "get",
 *     () -> cache.get(key), () -> !cache.contains(key));
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class CacheSpan {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>CacheSpan</code> \u5b9e\u4f8b\u3002 */
    private CacheSpan() {
    }

    /**
     * \u5305\u88c5\u7f13\u5b58\u8bfb\u64cd\u4f5c\u3002
     * @param biz \u4e1a\u52a1\u6807\u8bc6\uff08\u5982 "userCache"\uff09
     * @param backend \u540e\u7aef\u6807\u8bc6\uff08\u5982 "redis"\u3001"caffeine"\uff09
     * @param supplier \u7f13\u5b58\u8bfb\u64cd\u4f5c
     * @param missCheck \u672a\u547d\u4e2d\u68c0\u67e5\uff08true \u8868\u793a cache miss\uff09
     * @return supplier \u7684\u8fd4\u56de\u503c
     *
     * @param <T> \u8fd4\u56de\u503c\u7c7b\u578b
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
            boolean hit = Objects.nonNull(missCheck) && !missCheck.get();
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
