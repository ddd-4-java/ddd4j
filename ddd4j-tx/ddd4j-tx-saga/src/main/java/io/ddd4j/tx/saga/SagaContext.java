package io.ddd4j.tx.saga;

import io.ddd4j.tx.TransactionContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saga 执行上下文。
 *
 * <p>承载 Saga 状态、事务上下文和步骤执行结果，
 * 作为 Saga 步骤的 action/compensator 的入参传递。
 *
 * @param <S> Saga 状态类型
 * @author hiwepy
 * @since 4.0.0
 */
public class SagaContext<S> {

    private final S state;
    private final TransactionContext transactionContext;
    private final Map<String, Object> stepResults;
    private final String currentStepName;

    private SagaContext(Builder<S> builder) {
        this.state = builder.state;
        this.transactionContext = builder.transactionContext;
        this.stepResults = new ConcurrentHashMap<>(builder.stepResults);
        this.currentStepName = builder.currentStepName;
    }

    /**
     * 创建 Builder。
     */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    public S getState() {
        return state;
    }

    public TransactionContext getTransactionContext() {
        return transactionContext;
    }

    public Map<String, Object> getStepResults() {
        return stepResults;
    }

    @SuppressWarnings("unchecked")
    public <T> T getStepResult(String stepName) {
        return (T) stepResults.get(stepName);
    }

    public void setStepResult(String stepName, Object result) {
        stepResults.put(stepName, result);
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    /**
     * SagaContext Builder。
     */
    public static class Builder<S> {
        private S state;
        private TransactionContext transactionContext;
        private final Map<String, Object> stepResults = new ConcurrentHashMap<>();
        private String currentStepName;

        public Builder<S> state(S state) {
            this.state = state;
            return this;
        }

        public Builder<S> transactionContext(TransactionContext transactionContext) {
            this.transactionContext = transactionContext;
            return this;
        }

        public Builder<S> stepResult(String stepName, Object result) {
            this.stepResults.put(stepName, result);
            return this;
        }

        public Builder<S> currentStepName(String currentStepName) {
            this.currentStepName = currentStepName;
            return this;
        }

        public SagaContext<S> build() {
            return new SagaContext<>(this);
        }
    }
}
