package io.ddd4j.web.core;

import io.opentelemetry.api.trace.Span;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * ddd4j-web 内部辅助类：通过反射安全调用 ddd4j-extension-otel，
 * 避免对 OTel API 的编译时强依赖。
 *
 * <p>8 个 web 框架的拦截器/过滤器通过此类集成 WebOtelIntegration。
 * 若 ddd4j-extension-otel 不在 classpath 上，所有方法为 noop（反射失败 → 安全降级）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class WebOtelSupport {

    private static final Class<?> OTEL_INTEGRATION_CLASS = loadOtelClass();
    private static final Class<?> SPAN_CLASS = loadSpanClass();

    private WebOtelSupport() {
    }

    private static Class<?> loadOtelClass() {
        try {
            return Class.forName("io.ddd4j.extension.otel.WebOtelIntegration");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class<?> loadSpanClass() {
        try {
            return Class.forName("io.opentelemetry.api.trace.Span");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 启动 SERVER span，返回 Span（OTel 未就绪时返回 null）。
     */
    public static Object startServerSpan(String method, String path, Map<String, String> headers) {
        if (OTEL_INTEGRATION_CLASS == null) {
            return null;
        }
        try {
            Method m = OTEL_INTEGRATION_CLASS.getMethod("startServerSpan", String.class, String.class, Map.class);
            return m.invoke(null, method, path, headers == null ? new HashMap<>() : headers);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 激活 span 为当前 Context，返回 Scope（try-with-resources）。
     */
    public static AutoCloseable activate(Object span) {
        if (OTEL_INTEGRATION_CLASS == null || span == null) {
            return () -> {
            };
        }
        try {
            Method m = OTEL_INTEGRATION_CLASS.getMethod("activate", SPAN_CLASS != null ? SPAN_CLASS : Object.class);
            Object scope = m.invoke(null, span);
            return (AutoCloseable) scope;
        } catch (Throwable t) {
            return () -> {
            };
        }
    }

    /**
     * 记录异常。
     */
    public static void recordError(Object span, Throwable error) {
        if (OTEL_INTEGRATION_CLASS == null || span == null) {
            return;
        }
        try {
            Method m = OTEL_INTEGRATION_CLASS.getMethod("recordError", SPAN_CLASS != null ? SPAN_CLASS : Object.class,
                    Throwable.class);
            m.invoke(null, span, error);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * 结束 span 并记录 HTTP 状态码。
     */
    public static void endServerSpan(Object span, int status) {
        if (OTEL_INTEGRATION_CLASS == null || span == null) {
            return;
        }
        try {
            Method m = OTEL_INTEGRATION_CLASS.getMethod("endServerSpan", SPAN_CLASS != null ? SPAN_CLASS : Object.class,
                    int.class);
            m.invoke(null, span, status);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * 注入 traceparent 到响应头。
     */
    public static void injectResponseContext(Map<String, String> responseHeaders) {
        if (OTEL_INTEGRATION_CLASS == null || responseHeaders == null) {
            return;
        }
        try {
            Method m = OTEL_INTEGRATION_CLASS.getMethod("injectResponseContext", Map.class);
            m.invoke(null, responseHeaders);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * 检查 OTel 集成是否可用。
     */
    public static boolean isAvailable() {
        return OTEL_INTEGRATION_CLASS != null;
    }
}