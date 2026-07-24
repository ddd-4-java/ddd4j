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

    private EventSpan() {
    }

    /**
     * 包装领域事件发布。
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
     * 包装 CQRS 查询执行。
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
     * 包装投影执行。
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