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

/**
 * 领域事件的 OTel Span 辅助工具。
 *
 * <p>为 {@code DomainEventPublisher.publish()} 和读模型投影提供 span 包装。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class EventSpan {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>EventSpan</code> \u5b9e\u4f8b\u3002 */
    private EventSpan() {
    }

    /**
     * \u5305\u88c5\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u3002
     *
     * @param eventType \u4e8b\u4ef6\u7c7b\u578b
     * @param aggregateType \u805a\u5408\u7c7b\u578b
     * @param aggregateId \u805a\u5408\u6807\u8bc6
     * @param runnable \u5f85\u8ffd\u8e2a\u64cd\u4f5c
     */
    public static void publish(String eventType, String aggregateType, String aggregateId, Runnable runnable) {
        if (!Ddd4jOtel.isAvailable()) {
            runnable.run();
            return;
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.event.publish")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(Ddd4jOtel.ATTR_DOMAIN_EVENT_TYPE, eventType)
                .setAttribute(Ddd4jOtel.ATTR_AGGREGATE_TYPE, aggregateType)
                .setAttribute(Ddd4jOtel.ATTR_AGGREGATE_ID, aggregateId)
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

    /**
     * \u5305\u88c5 CQRS \u67e5\u8be2\u6267\u884c\u3002
     *
     * @param <T> \u8fd4\u56de\u503c\u7c7b\u578b
     * @param queryType \u67e5\u8be2\u7c7b\u578b
     * @param supplier \u5f85\u8ffd\u8e2a\u64cd\u4f5c
     * @return \u67e5\u8be2\u7ed3\u679c
     */
    public static <T> T query(String queryType, java.util.function.Supplier<T> supplier) {
        if (!Ddd4jOtel.isAvailable()) {
            return supplier.get();
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.query.execute")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(Ddd4jOtel.ATTR_QUERY_TYPE, queryType)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            return supplier.get();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * \u5305\u88c5\u6295\u5f71\u6267\u884c\u3002
     *
     * @param <T> \u8fd4\u56de\u503c\u7c7b\u578b
     * @param projectionName \u6295\u5f71\u540d\u79f0
     * @param supplier \u5f85\u8ffd\u8e2a\u64cd\u4f5c
     * @return \u6295\u5f71\u7ed3\u679c
     */
    public static <T> T projection(String projectionName, java.util.function.Supplier<T> supplier) {
        if (!Ddd4jOtel.isAvailable()) {
            return supplier.get();
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.projection.run")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(Ddd4jOtel.ATTR_PROJECTION_NAME, projectionName)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            return supplier.get();
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
            throw t;
        } finally {
            span.end();
        }
    }
}
