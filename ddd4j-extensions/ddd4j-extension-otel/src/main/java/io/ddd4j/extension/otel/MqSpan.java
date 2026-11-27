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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

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

    /** \u5c06\u8ddf\u8e2a\u4e0a\u4e0b\u6587\u5199\u5165 HTTP \u6216\u6d88\u606f\u4f20\u8f93\u5934\u7684\u4f20\u64ad\u9002\u914d\u5668\u3002 */
    private static final TextMapSetter<Map<String, String>> SETTER = new TextMapSetter<Map<String, String>>() {
        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            if (Objects.nonNull(carrier)) {
                carrier.put(key, value);
            }
        }
    };
    /** \u4ece HTTP \u6216\u6d88\u606f\u4f20\u8f93\u5934\u8bfb\u53d6\u8ddf\u8e2a\u4e0a\u4e0b\u6587\u7684\u4f20\u64ad\u9002\u914d\u5668\u3002 */
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return Objects.isNull(carrier) ? Collections.emptyList() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            if (Objects.isNull(carrier)) {
                return null;
            }
            String value = carrier.get(key);
            if (Objects.isNull(value)) {
                for (Map.Entry<String, String> entry : carrier.entrySet()) {
                    if (Objects.nonNull(entry.getKey()) && entry.getKey().equalsIgnoreCase(key)) {
                        return entry.getValue();
                    }
                }
            }
            return value;
        }
    };

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>MqSpan</code> \u5b9e\u4f8b\u3002 */
    private MqSpan() {
    }

    /**
     * 创建 PRODUCER span 并将 TraceContext 注入消息 headers。
     * @param broker 消息中间件连接地址
     * @param topic 消息主题
     * @param headers 消息头集合
     * @param runnable 待执行任务
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
     * @param broker 消息中间件连接地址
     * @param topic 消息主题
     * @param headers 消息头集合
     * @return 返回的 Scope 结果
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
     * @param span 链路 Span 对象
     * @param error 错误对象
     */
    public static void endConsumer(Span span, Throwable error) {
        if (Objects.isNull(span)) {
            return;
        }
        if (Objects.nonNull(error)) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getClass().getSimpleName());
        }
        span.end();
    }

    /**
     * 完成 CONSUMER span（正常）。
     * @param span 链路 Span 对象
     */
    public static void endConsumer(Span span) {
        endConsumer(span, null);
    }

    /**
     * \u628a\u5f53\u524d\u8ffd\u8e2a\u4e0a\u4e0b\u6587\u5199\u5165\u6d88\u606f\u4f20\u8f93\u5934\u3002
     * @param headers \u4f20\u64ad\u8ffd\u8e2a\u4fe1\u606f\u4f7f\u7528\u7684\u4f20\u8f93\u5934\u5b57\u6bb5
     */
    private static void injectContext(Map<String, String> headers) {
        if (Objects.isNull(headers)) {
            return;
        }
        try {
            TextMapPropagator propagator = io.opentelemetry.api.GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
            if (Objects.isNull(propagator)) {
                return;
            }
            propagator.inject(Context.current(), headers, SETTER);
        } catch (Throwable ignored) {
            // 注入失败不应影响消息发送
        }
    }

    /**
     * \u4ece\u6d88\u606f\u5934\u6062\u590d\u8c03\u7528\u94fe\u4f20\u64ad\u4e0a\u4e0b\u6587\u3002
     * @param headers \u4f20\u64ad\u8ffd\u8e2a\u4fe1\u606f\u4f7f\u7528\u7684\u4f20\u8f93\u5934\u5b57\u6bb5
     * @return \u6d88\u606f\u4e2d\u4f20\u9012\u7684 OpenTelemetry \u4e0a\u4e0b\u6587
     */
    private static Context extractContext(Map<String, String> headers) {
        if (Objects.isNull(headers)) {
            return Context.current();
        }
        try {
            TextMapPropagator propagator = io.opentelemetry.api.GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
            if (Objects.isNull(propagator)) {
                return Context.current();
            }
            return propagator.extract(Context.current(), headers, GETTER);
        } catch (Throwable t) {
            return Context.current();
        }
    }
}
