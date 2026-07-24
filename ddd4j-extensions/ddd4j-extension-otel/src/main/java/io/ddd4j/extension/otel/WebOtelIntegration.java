package io.ddd4j.extension.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.HashMap;
import java.util.Map;

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

    private WebOtelIntegration() {
    }

    /**
     * 提取上游 TraceContext 并创建 SERVER span。
     *
     * @param method     HTTP 方法（GET/POST/...）
     * @param route      路由模板
     * @param headers    请求头（用于提取 traceparent）
     * @return 已开启的 Span（OTel 未配置时返回 invalid span，noop）
     */
    public static Span startServerSpan(String method, String route, Map<String, String> headers) {
        if (!Ddd4jOtel.isAvailable()) {
            return io.opentelemetry.api.trace.Span.getInvalid();
        }
        Context parent = HttpSpan.extractContext(headers == null ? new HashMap<>() : headers);
        return HttpSpan.serverSpan(method, route, parent);
    }

    /**
     * 激活 Span 为当前 Context，返回 Scope（try-with-resources）。
     */
    public static Scope activate(Span span) {
        return HttpSpan.makeCurrent(span);
    }

    /**
     * 记录异常。
     */
    public static void recordError(Span span, Throwable t) {
        HttpSpan.recordError(span, t);
    }

    /**
     * 结束 span 并记录 HTTP 状态码。
     */
    public static void endServerSpan(Span span, int status) {
        HttpSpan.endServerSpan(span, status);
    }

    /**
     * 注入 traceparent 到响应头。
     */
    public static void injectResponseContext(Map<String, String> responseHeaders) {
        HttpSpan.injectContext(responseHeaders);
    }

    /**
     * 通用 helper：从 HttpHeaders 风格（Map）提取。
     */
    public static Map<String, String> asHeaders(Map<String, String> headers) {
        return headers == null ? new HashMap<>() : new HashMap<>(headers);
    }
}