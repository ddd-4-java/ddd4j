package io.ddd4j.extension.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Collections;
import java.util.Map;

/**
 * 消息队列的 OTel Span 辅助工具。
 *
 * <p>为 {@link io.ddd4j.mq.MQClient} 提供 PRODUCER / CONSUMER span，
 * 支持 W3C TraceContext 跨消息传播。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class MqSpan {

    private MqSpan() {
    }

    /**
     * 创建 PRODUCER span 并将 TraceContext 注入消息 headers。
     */
    public static void producer(String broker, String topic, Map<String, String> headers, Runnable runnable) {
        if (!Ddd4jOtel.isAvailable()) {
            runnable.run();
            return;
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.mq.send")
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute(Ddd4jOtel.ATTR_MESSAGING_SYSTEM, broker)
                .setAttribute(Ddd4jOtel.ATTR_MESSAGING_DESTINATION, topic)
                .setAttribute(Ddd4jOtel.ATTR_MESSAGING_OPERATION, "publish")
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Ddd4jOtel.enrichWithBusinessContext(span);
            injectContext(headers);
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
     * 从消息 headers 提取 W3C TraceContext，并创建 CONSUMER span。
     */
    public static Scope consumer(String broker, String topic, Map<String, String> headers) {
        if (!Ddd4jOtel.isAvailable()) {
            return Scope.noop();
        }
        Context parent = extractContext(headers);
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("ddd4j.mq.receive")
                .setSpanKind(SpanKind.CONSUMER)
                .setParent(parent)
                .setAttribute(Ddd4jOtel.ATTR_MESSAGING_SYSTEM, broker)
                .setAttribute(Ddd4jOtel.ATTR_MESSAGING_DESTINATION, topic)
                .setAttribute(Ddd4jOtel.ATTR_MESSAGING_OPERATION, "process")
                .startSpan();
        Ddd4jOtel.enrichWithBusinessContext(span);
        return span.makeCurrent();
    }

    /**
     * 完成 CONSUMER span（异常标记）。
     */
    public static void endConsumer(Span span, Throwable error) {
        if (span == null) {
            return;
        }
        if (error != null) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getClass().getSimpleName());
        }
        span.end();
    }

    /**
     * 完成 CONSUMER span（正常）。
     */
    public static void endConsumer(Span span) {
        endConsumer(span, null);
    }

    private static final TextMapSetter<Map<String, String>> SETTER = new TextMapSetter<Map<String, String>>() {
        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            if (carrier != null) {
                carrier.put(key, value);
            }
        }
    };

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier == null ? Collections.emptyList() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            if (carrier == null) {
                return null;
            }
            String value = carrier.get(key);
            if (value == null) {
                for (Map.Entry<String, String> entry : carrier.entrySet()) {
                    if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                        return entry.getValue();
                    }
                }
            }
            return value;
        }
    };

    private static void injectContext(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        try {
            TextMapPropagator propagator = io.opentelemetry.api.GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
            if (propagator == null) {
                return;
            }
            propagator.inject(Context.current(), headers, SETTER);
        } catch (Throwable ignored) {
            // 注入失败不应影响消息发送
        }
    }

    private static Context extractContext(Map<String, String> headers) {
        if (headers == null) {
            return Context.current();
        }
        try {
            TextMapPropagator propagator = io.opentelemetry.api.GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
            if (propagator == null) {
                return Context.current();
            }
            return propagator.extract(Context.current(), headers, GETTER);
        } catch (Throwable t) {
            return Context.current();
        }
    }
}