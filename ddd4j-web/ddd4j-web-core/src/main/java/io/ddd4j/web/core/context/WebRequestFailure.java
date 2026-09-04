package io.ddd4j.web.core.context;

/**
 * 可由运行时事件总线观测的框架无关 HTTP 请求失败事件。
 *
 * <p>2026-09-04：从 2.0.x record 形式翻译为 JDK 8 兼容的传统 class（1.0.x 行适配）。</p>
 */
public final class WebRequestFailure {

    private final String method;
    private final String path;
    private final Throwable cause;

    public WebRequestFailure(String method, String path, Throwable cause) {
        this.method = method;
        this.path = path;
        this.cause = cause;
    }

    public String method() { return method; }
    public String path() { return path; }
    public Throwable cause() { return cause; }
}