package io.ddd4j.extension.qlexpress.model;

/**
 * 不抛出异常的表达式执行结果。
 *
 * 2026-09-05：record 降级为 JDK8 兼容 class。
 *
 * @param <T> 结果类型
 */
public final class QLExpressExecutionResult<T> {

    private final boolean success;
    private final T value;
    private final String errorCode;
    private final String errorMessage;
    private final long elapsedNanos;

    private QLExpressExecutionResult(boolean success, T value, String errorCode,
                                     String errorMessage, long elapsedNanos) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.elapsedNanos = elapsedNanos;
    }

    public static <T> QLExpressExecutionResult<T> success(T value, long elapsedNanos) {
        return new QLExpressExecutionResult<T>(true, value, null, null, elapsedNanos);
    }

    public static <T> QLExpressExecutionResult<T> failure(String errorCode, String errorMessage,
                                                          long elapsedNanos) {
        return new QLExpressExecutionResult<T>(false, null, errorCode, errorMessage, elapsedNanos);
    }

    public boolean success() { return success; }
    public T value() { return value; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public long elapsedNanos() { return elapsedNanos; }
}
