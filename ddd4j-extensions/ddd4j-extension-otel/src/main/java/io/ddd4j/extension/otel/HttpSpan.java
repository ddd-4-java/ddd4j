package io.ddd4j.extension.otel;

import io.opentelemetry.api.common.AttributeKey;
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
 * HTTP 入站 span 工具（Web 框架集成点 #1）。
 *
 * <p>为 8 个 web 框架（webmvc / webflux / javalin / quarkus / micronaut / dropwizard / vertx / helidon）
 * 提供统一的 HTTP SERVER span 入口：
 * <ul>
 *   <li>提取上游 traceparent（W3C TraceContext）</li>
 *   <li>创建 SERVER span（包含 HTTP method / route / status / 等属性）</li>
 *   <li>完成 span 时自动 setStatus(ERROR) on 4xx/5xx</li>
 *   <li>可选：注入下游 traceparent 到响应头</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 提取上游 context 并开启 span
 * Context parent = HttpSpan.extractContext(requestHeaders);
 * Span span = HttpSpan.serverSpan("GET", "/api/orders", parent);
 * try (Scope scope = span.makeCurrent()) {
 *     // 业务逻辑
 * } catch (Throwable t) {
 *     HttpSpan.recordError(span, t);
 *     throw t;
 * } finally {
 *     HttpSpan.endServerSpan(span, 200);
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class HttpSpan {

    /** 标准化 HTTP 属性键（遵循 OTel 语义约定）。 */
    public static final AttributeKey<String> ATTR_HTTP_METHOD = AttributeKey.stringKey("http.request.method");
    public static final AttributeKey<String> ATTR_HTTP_ROUTE = AttributeKey.stringKey("http.route");
    public static final AttributeKey<String> ATTR_HTTP_STATUS = AttributeKey.stringKey("http.response.status_code");
    public static final AttributeKey<String> ATTR_URL_PATH = AttributeKey.stringKey("url.path");
    public static final AttributeKey<String> ATTR_URL_SCHEME = AttributeKey.stringKey("url.scheme");
    public static final AttributeKey<String> ATTR_USER_AGENT = AttributeKey.stringKey("user_agent.original");
    public static final AttributeKey<String> ATTR_CLIENT_IP = AttributeKey.stringKey("client.address");

    /** 业务级属性。 */
    public static final AttributeKey<String> ATTR_DDD4J_REQUEST_ID = AttributeKey.stringKey("ddd4j.request.id");
    public static final AttributeKey<String> ATTR_DDD4J_TENANT_ID = AttributeKey.stringKey("ddd4j.tenant.id");

    private HttpSpan() {
    }

    /**
     * 从请求 headers 提取 W3C TraceContext。
     *
     * @param headers 请求头 Map
     * @return 上游 Context（若无上游则返回 Context.current()）
     */
    public static Context extractContext(Map<String, String> headers) {
        if (Objects.isNull(headers) || headers.isEmpty()) {
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

    /**
     * 创建 SERVER span。
     *
     * @param method   HTTP 方法（GET/POST/...）
     * @param route    路由模板（如 /api/users/{id}）
     * @param parent   上游 Context（来自 extractContext）
     * @return 已开启的 Span
     */
    public static Span serverSpan(String method, String route, Context parent) {
        if (!Ddd4jOtel.isAvailable()) {
            return io.opentelemetry.api.trace.Span.getInvalid();
        }
        Tracer tracer = Ddd4jOtel.tracer();
        Span span = tracer.spanBuilder("HTTP " + method + " " + route)
                .setSpanKind(SpanKind.SERVER)
                .setParent(parent)
                .setAttribute(ATTR_HTTP_METHOD, Objects.isNull(method) ? "UNKNOWN" : method)
                .setAttribute(ATTR_HTTP_ROUTE, Objects.isNull(route) ? "" : route)
                .startSpan();
        Ddd4jOtel.enrichWithBusinessContext(span);
        return span;
    }

    /**
     * 设置 span 状态（4xx/5xx 视为 ERROR）。
     *
     * @param span   目标 span
     * @param status HTTP 状态码
     */
    public static void endServerSpan(Span span, int status) {
        if (Objects.isNull(span) || !span.getSpanContext().isValid()) {
            return;
        }
        span.setAttribute(ATTR_HTTP_STATUS, String.valueOf(status));
        if (status >= 500) {
            span.setStatus(StatusCode.ERROR, "HTTP " + status);
        } else if (status >= 400) {
            span.setStatus(StatusCode.ERROR, "HTTP " + status);
        }
        span.end();
    }

    /**
     * 在 span 上记录异常并标记 ERROR。
     */
    public static void recordError(Span span, Throwable t) {
        if (Objects.isNull(span) || Objects.isNull(t)) {
            return;
        }
        span.recordException(t);
        span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
    }

    /**
     * 注入 traceparent 到响应 headers。
     *
     * @param headers 响应头 Map
     */
    public static void injectContext(Map<String, String> headers) {
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
            // 注入失败不应影响响应
        }
    }

    /**
     * 安全获取当前 Context 的 Scope（noop fallback）。
     */
    public static Scope makeCurrent(Span span) {
        if (Objects.isNull(span) || !span.getSpanContext().isValid()) {
            return Scope.noop();
        }
        return span.makeCurrent();
    }

    private static final TextMapSetter<Map<String, String>> SETTER = new TextMapSetter<Map<String, String>>() {
        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            if (Objects.nonNull(carrier)) {
                carrier.put(key, value);
            }
        }
    };

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
}
