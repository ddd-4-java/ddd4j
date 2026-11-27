package io.ddd4j.extension.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 8 个 web 框架的统一 OTel 入口点（Web 框架集成点 #1）。
 *
 * <p>提供与框架无关的 HTTP span 生命周期管理：
 * <ul>
 *   <li>{@link #startServerSpan} - 提取 W3C TraceContext 并创建 SERVER span</li>
 *   <li>{@link #endServerSpan} - 记录 HTTP 状态码并结束 span</li>
 *   <li>{@link #recordError} - 记录异常</li>
 *   <li>{@link #injectResponseContext} - 注入 traceparent 到响应头</li>
 * </ul>
 *
 * <h3>框架适配器使用示例</h3>
 * <pre>{@code
 * // Spring WebMVC / Dropwizard / Vertx / Helidon（Servlet API）
 * Map<String, String> headers = new HashMap<>();
 * for (String name : Collections.list(request.getHeaderNames())) {
 *     headers.put(name, request.getHeader(name));
 * }
 * Span span = WebOtelIntegration.startServerSpan(
 *     request.getMethod(), "/api/users", headers);
 * try (Scope scope = WebOtelIntegration.activate(span)) {
 *     return true; // preHandle
 * } catch (Throwable t) {
 *     WebOtelIntegration.recordError(span, t);
 *     throw t;
 * }
 * // postHandle / afterCompletion:
 * WebOtelIntegration.endServerSpan(span, response.getStatus());
 * Map<String, String> responseHeaders = new HashMap<>();
 * responseHeaders.forEach((k, v) -> WebOtelIntegration.injectResponseContext(headers));
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class WebOtelIntegration {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebOtelIntegration</code> \u5b9e\u4f8b\u3002 */
    private WebOtelIntegration() {
    }

    /**
     * 提取上游 TraceContext 并创建 SERVER span。
     *
     * @param method  HTTP 方法（GET/POST/...）
     * @param route   路由模板
     * @param headers 请求头（用于提取 traceparent）
     * @return 已开启的 Span（OTel 未配置时返回 invalid span，noop）
     */
    public static Span startServerSpan(String method, String route, Map<String, String> headers) {
        if (!Ddd4jOtel.isAvailable()) {
            return io.opentelemetry.api.trace.Span.getInvalid();
        }
        Context parent = HttpSpan.extractContext(Objects.isNull(headers) ? new HashMap<>() : headers);
        return HttpSpan.serverSpan(method, route, parent);
    }

    /**
     * \u6fc0\u6d3b Span \u4e3a\u5f53\u524d Context\uff0c\u8fd4\u56de Scope\uff08try-with-resources\uff09\u3002
     *
     * @param span \u8ffd\u8e2a\u7247\u6bb5
     * @return \u6fc0\u6d3b\u7684\u8ffd\u8e2a\u4f5c\u7528\u57df
     */
    public static Scope activate(Span span) {
        return HttpSpan.makeCurrent(span);
    }

    /**
     * \u8bb0\u5f55\u5f02\u5e38\u3002
     *
     * @param span \u8ffd\u8e2a\u7247\u6bb5
     * @param t \u5f02\u5e38
     */
    public static void recordError(Span span, Throwable t) {
        HttpSpan.recordError(span, t);
    }

    /**
     * \u7ed3\u675f span \u5e76\u8bb0\u5f55 HTTP \u72b6\u6001\u7801\u3002
     *
     * @param span \u8ffd\u8e2a\u7247\u6bb5
     * @param status HTTP \u72b6\u6001\u7801
     */
    public static void endServerSpan(Span span, int status) {
        HttpSpan.endServerSpan(span, status);
    }

    /**
     * \u6ce8\u5165 traceparent \u5230\u54cd\u5e94\u5934\u3002
     *
     * @param responseHeaders \u54cd\u5e94\u5934
     */
    public static void injectResponseContext(Map<String, String> responseHeaders) {
        HttpSpan.injectContext(responseHeaders);
    }

    /**
     * \u901a\u7528 helper\uff1a\u4ece HttpHeaders \u98ce\u683c\uff08Map\uff09\u63d0\u53d6\u3002
     *
     * @param headers \u6d88\u606f\u5934
     * @return \u6d88\u606f\u5934\u6620\u5c04
     */
    public static Map<String, String> asHeaders(Map<String, String> headers) {
        return Objects.isNull(headers) ? new HashMap<>() : new HashMap<>(headers);
    }
}
