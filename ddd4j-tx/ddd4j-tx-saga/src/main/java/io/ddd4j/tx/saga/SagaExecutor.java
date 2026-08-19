package io.ddd4j.tx.saga;

import io.ddd4j.tx.TransactionContext;
import io.ddd4j.tx.TransactionException;

/**
 * Saga 执行器。
 *
 * <p>驱动 Saga 状态机执行：按步骤顺序执行 action，失败时逆序执行 compensator。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * SagaExecutor<OrderSagaState> executor = new SagaExecutor<>(sagaDefinition);
 * SagaResult<OrderSagaState> result = executor.execute(orderState, transactionContext);
 * if (result.isSuccessful()) {
 *     // 所有步骤成功
 * } else {
 *     // 补偿完成或部分补偿失败
 *     Throwable failure = result.getFailure();
 * }
 * }</pre>
 *
 * @param <S> Saga 状态类型
 * @author hiwepy
 * @since 4.0.0
 * @see SagaDefinition
 * @see SagaStep
 */
public class SagaExecutor<S> {

    private final SagaDefinition<S> definition;

    public SagaExecutor(SagaDefinition<S> definition) {
        this.definition = definition;
    }

    /**
     * 执行 Saga。
     *
     * @param state              Saga 状态
     * @param transactionContext 事务上下文
     * @return 执行结果
     */
    public SagaResult<S> execute(S state, TransactionContext transactionContext) {
        SagaContext<S> context = SagaContext.<S>builder()
                .state(state)
                .transactionContext(transactionContext)
                .build();

        int completedSteps = 0;
        Exception failure = null;

        // 正向执行
        for (SagaStep<S> step : definition.getSteps()) {
            try {
                context = SagaContext.<S>builder()
                        .state(state)
                        .transactionContext(transactionContext)
                        .currentStepName(step.getName())
                        .build();
                step.getAction().accept(context);
                completedSteps++;
            } catch (Exception e) {
                failure = e;
                break;
            }
        }

        // 全部成功
        if (failure == null) {
            return SagaResult.success(context);
        }

        // 逆序补偿
        for (int i = completedSteps - 1; i >= 0; i--) {
            SagaStep<S> step = definition.getSteps().get(i);
            if (step.hasCompensator()) {
                try {
                    step.getCompensator().accept(context);
                } catch (Exception compensatorException) {
                    failure.addSuppressed(compensatorException);
                }
            }
        }

        return SagaResult.failure(context, failure);
    }

    /**
     * 获取 Saga 定义。
     */
    public SagaDefinition<S> getDefinition() {
        return definition;
    }
}
