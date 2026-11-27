package io.ddd4j.web.core.observability;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ddd4j-web 内部辅助类：通过纯反射安全调用 ddd4j-extension-otel，
 * 避免对 OTel API 的任何编译时依赖。
 *
 * <p>8 个 web 框架的拦截器/过滤器通过此类集成 WebOtelIntegration。
 * 若 ddd4j-extension-otel 不在 classpath 上，所有方法为 noop（反射失败 → 安全降级）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class WebOtelSupport {

    /** Web \u8bf7\u6c42\u53ef\u9009 OpenTelemetry \u6865\u63a5\u7ec4\u4ef6\u7684\u7c7b\u540d\u3002 */
    private static final Class<?> OTEL_INTEGRATION_CLASS;
    /** \u542f\u52a8\u670d\u52a1\u7aef Span \u7684\u53cd\u5c04\u65b9\u6cd5\u3002 */
    private static final Method START_SERVER_SPAN;
    /** \u5728\u5f53\u524d\u6267\u884c\u7ebf\u7a0b\u6fc0\u6d3b Span \u7684\u53cd\u5c04\u65b9\u6cd5\u3002 */
    private static final Method ACTIVATE;
    /** \u5411\u670d\u52a1\u7aef Span \u8bb0\u5f55\u9519\u8bef\u7684\u53cd\u5c04\u65b9\u6cd5\u3002 */
    private static final Method RECORD_ERROR;
    /** \u4ee5\u6700\u7ec8 HTTP \u72b6\u6001\u7ed3\u675f\u670d\u52a1\u7aef Span \u7684\u53cd\u5c04\u65b9\u6cd5\u3002 */
    private static final Method END_SERVER_SPAN;
    /** \u5c06\u8ddf\u8e2a\u4fe1\u606f\u5199\u5165 HTTP \u54cd\u5e94\u7684\u53cd\u5c04\u65b9\u6cd5\u3002 */
    private static final Method INJECT_RESPONSE_CONTEXT;
    /** \u68c0\u67e5\u53ef\u9009 OpenTelemetry \u7ec4\u4ef6\u662f\u5426\u53ef\u7528\u7684\u53cd\u5c04\u65b9\u6cd5\u3002 */
    private static final Method IS_AVAILABLE;

    static {
        Class<?> cls = null;
        Method startSpan = null;
        Method act = null;
        Method recErr = null;
        Method endSpan = null;
        Method inject = null;
        Method isAvail = null;
        try {
            cls = Class.forName("io.ddd4j.extension.otel.WebOtelIntegration");
            startSpan = cls.getMethod("startServerSpan", String.class, String.class, Map.class);
            act = cls.getMethod("activate", Object.class);
            recErr = cls.getMethod("recordError", Object.class, Throwable.class);
            endSpan = cls.getMethod("endServerSpan", Object.class, int.class);
            inject = cls.getMethod("injectResponseContext", Map.class);
            isAvail = cls.getMethod("isAvailable");
        } catch (Throwable ignored) {
            // OTel 集成不在 classpath 上 → 所有方法降级为 noop
        }
        OTEL_INTEGRATION_CLASS = cls;
        START_SERVER_SPAN = startSpan;
        ACTIVATE = act;
        RECORD_ERROR = recErr;
        END_SERVER_SPAN = endSpan;
        INJECT_RESPONSE_CONTEXT = inject;
        IS_AVAILABLE = isAvail;
    }

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebOtelSupport</code> \u5b9e\u4f8b\u3002 */
    private WebOtelSupport() {
    }

    /**
     * \u542f\u52a8 SERVER span\uff0c\u8fd4\u56de span \u5bf9\u8c61\uff08OTel \u672a\u5c31\u7eea\u65f6\u8fd4\u56de null\uff09\u3002
     *
     * @param method HTTP \u65b9\u6cd5
     * @param path \u8bf7\u6c42\u8def\u5f84
     * @param headers \u8bf7\u6c42\u5934
     * @return \u670d\u52a1\u7aef\u8ffd\u8e2a\u7247\u6bb5
     */
    public static Object startServerSpan(String method, String path, Map<String, String> headers) {
        if (Objects.isNull(START_SERVER_SPAN)) {
            return null;
        }
        try {
            return START_SERVER_SPAN.invoke(null, method, path, Objects.isNull(headers) ? new HashMap<>() : headers);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * \u6fc0\u6d3b span \u4e3a\u5f53\u524d Context\uff0c\u8fd4\u56de Scope\uff08try-with-resources \u53ef\u5173\u95ed\uff09\u3002
     *
     * @param span \u8ffd\u8e2a\u7247\u6bb5
     * @return \u8ffd\u8e2a\u4f5c\u7528\u57df
     */
    public static AutoCloseable activate(Object span) {
        if (Objects.isNull(ACTIVATE) || Objects.isNull(span)) {
            return () -> {
            };
        }
        try {
            Object scope = ACTIVATE.invoke(null, span);
            if (scope instanceof AutoCloseable) {
                return (AutoCloseable) scope;
            }
            return () -> {
            };
        } catch (Throwable t) {
            return () -> {
            };
        }
    }

    /**
     * \u8bb0\u5f55\u5f02\u5e38\u5230 span\u3002
     *
     * @param span \u8ffd\u8e2a\u7247\u6bb5
     * @param error Web \u9519\u8bef
     */
    public static void recordError(Object span, Throwable error) {
        if (Objects.isNull(RECORD_ERROR) || Objects.isNull(span) || Objects.isNull(error)) {
            return;
        }
        try {
            RECORD_ERROR.invoke(null, span, error);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * \u7ed3\u675f span \u5e76\u8bb0\u5f55 HTTP \u72b6\u6001\u7801\u3002
     *
     * @param span \u8ffd\u8e2a\u7247\u6bb5
     * @param status HTTP \u72b6\u6001\u7801
     */
    public static void endServerSpan(Object span, int status) {
        if (Objects.isNull(END_SERVER_SPAN) || Objects.isNull(span)) {
            return;
        }
        try {
            END_SERVER_SPAN.invoke(null, span, status);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * \u6ce8\u5165 traceparent \u5230\u54cd\u5e94\u5934\u3002
     *
     * @param responseHeaders \u54cd\u5e94\u5934
     */
    public static void injectResponseContext(Map<String, String> responseHeaders) {
        if (Objects.isNull(INJECT_RESPONSE_CONTEXT) || Objects.isNull(responseHeaders)) {
            return;
        }
        try {
            INJECT_RESPONSE_CONTEXT.invoke(null, responseHeaders);
        } catch (Throwable ignored) {
            // 静默失败
        }
    }

    /**
     * \u68c0\u67e5 OTel \u96c6\u6210\u662f\u5426\u53ef\u7528\u3002
     *
     * @return OTel \u53ef\u7528\u65f6\u4e3a true
     */
    public static boolean isAvailable() {
        if (Objects.isNull(IS_AVAILABLE)) {
            return false;
        }
        try {
            Object result = IS_AVAILABLE.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            return false;
        }
    }
}
