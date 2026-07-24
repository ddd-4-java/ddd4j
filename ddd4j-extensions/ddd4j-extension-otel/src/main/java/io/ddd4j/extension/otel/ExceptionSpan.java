package io.ddd4j.extension.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

/**
 * 异常的 OTel Span 事件记录工具。
 *
 * <p>为 {@code GlobalExceptionHandler.logException()} 提供标准化错误事件。
 * 无 OTel 时为 noop。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在 GlobalExceptionHandler.logException() 中
 * ExceptionSpan.record(ex);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class ExceptionSpan {

    private ExceptionSpan() {
    }

    /**
     * 在当前 Span 上记录异常。
     *
     * @param throwable 异常
     */
    public static void record(Throwable throwable) {
        if (throwable == null || !Ddd4jOtel.isAvailable()) {
            return;
        }
        try {
            Span span = Span.current();
            if (!span.getSpanContext().isValid()) {
                return;
            }
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR, throwable.getClass().getSimpleName());
        } catch (Throwable ignored) {
            // 异常记录不应影响主流程
        }
    }

    /**
     * 在指定 Span 上记录异常。
     */
    public static void record(Span span, Throwable throwable) {
        if (throwable == null || span == null) {
            return;
        }
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR, throwable.getClass().getSimpleName());
    }
}