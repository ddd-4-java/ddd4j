package io.ddd4j.tx.saga;

/**
 * Saga 执行结果。
 *
 * @param <S> Saga 状态类型
 * @author hiwepy
 * @since 4.0.0
 */
public class SagaResult<S> {

    private final boolean successful;
    private final SagaContext<S> context;
    private final Exception failure;

    private SagaResult(boolean successful, SagaContext<S> context, Exception failure) {
        this.successful = successful;
        this.context = context;
        this.failure = failure;
    }

    /**
     * 创建成功结果。
     */
    public static <S> SagaResult<S> success(SagaContext<S> context) {
        return new SagaResult<>(true, context, null);
    }

    /**
     * 创建失败结果。
     */
    public static <S> SagaResult<S> failure(SagaContext<S> context, Exception failure) {
        return new SagaResult<>(false, context, failure);
    }

    /**
     * 是否执行成功。
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * 获取执行上下文。
     */
    public SagaContext<S> getContext() {
        return context;
    }

    /**
     * 获取失败原因（如果失败）。
     */
    public Exception getFailure() {
        return failure;
    }
}
