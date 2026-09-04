package io.ddd4j.extension.qlexpress.model;

/**
 * 不抛出异常的表达式执行结果。
 *
 * @param success      是否成功
 * @param value        表达式原始结果值
 * @param errorCode    异常类型
 * @param errorMessage 异常消息
 * @param elapsedNanos 执行耗时，单位纳秒
 * @param <T>           结果类型
 */
public record QLExpressExecutionResult<T>(boolean success, T value, String errorCode,
                                          String errorMessage, long elapsedNanos) {

    public static <T> QLExpressExecutionResult<T> success(T value, long elapsedNanos) {
        return new QLExpressExecutionResult<>(true, value, null, null, elapsedNanos);
    }

    public static <T> QLExpressExecutionResult<T> failure(String errorCode, String errorMessage,
                                                          long elapsedNanos) {
        return new QLExpressExecutionResult<>(false, null, errorCode, errorMessage, elapsedNanos);
    }
}
