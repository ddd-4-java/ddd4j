package io.ddd4j.tx.saga;

import java.util.List;
import java.util.Objects;

/**
 * Saga 状态机定义（DSL 风格）。
 *
 * <p>使用示例：
 * <pre>{@code
 * SagaDefinition<OrderSagaState> saga = SagaDefinition.<OrderSagaState>builder()
 *     .name("order-creation")
 *     .step("create-order")
 *         .action(ctx -> orderService.create(ctx.getState()))
 *         .compensator(ctx -> orderService.cancel(ctx.getState().getOrderId()))
 *     .step("deduct-inventory")
 *         .action(ctx -> inventoryService.deduct(ctx.getState().getSku(), ctx.getState().getQuantity()))
 *         .compensator(ctx -> inventoryService.restore(ctx.getState().getSku(), ctx.getState().getQuantity()))
 *     .step("process-payment")
 *         .action(ctx -> paymentService.charge(ctx.getState().getOrderId(), ctx.getState().getAmount()))
 *         .compensator(ctx -> paymentService.refund(ctx.getState().getOrderId(), ctx.getState().getAmount()))
 *     .build();
 * }</pre>
 *
 * @param <S> Saga 状态类型
 * @author hiwepy
 * @since 4.0.0
 * @see SagaStep
 * @see SagaExecutor
 */
public class SagaDefinition<S> {

    private final String name;
    private final List<SagaStep<S>> steps;

    private SagaDefinition(Builder<S> builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.steps = List.copyOf(builder.steps);
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("Saga must have at least one step");
        }
    }

    /**
     * 创建 Builder。
     */
    public static <S> Builder<S> builder() {
        return new Builder<>();
    }

    /**
     * 获取 Saga 名称。
     */
    public String getName() {
        return name;
    }

    /**
     * 获取所有步骤（按顺序）。
     */
    public List<SagaStep<S>> getSteps() {
        return steps;
    }

    /**
     * 获取步骤数量。
     */
    public int getStepCount() {
        return steps.size();
    }

    /**
     * SagaDefinition Builder。
     */
    public static class Builder<S> {
        private String name;
        private final List<SagaStep<S>> steps = new java.util.ArrayList<>();
        private SagaStep.Builder<S> currentStepBuilder;

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 开始定义新步骤。
         */
        public Builder<S> step(String stepName) {
            // 完成上一步
            if (currentStepBuilder != null) {
                steps.add(currentStepBuilder.build());
            }
            currentStepBuilder = SagaStep.<S>builder().name(stepName);
            return this;
        }

        /**
         * 设置当前步骤的 action。
         */
        public Builder<S> action(java.util.function.Consumer<SagaContext<S>> action) {
            if (currentStepBuilder == null) {
                throw new IllegalStateException("Must call step() before action()");
            }
            currentStepBuilder.action(action);
            return this;
        }

        /**
         * 设置当前步骤的 compensator。
         */
        public Builder<S> compensator(java.util.function.Consumer<SagaContext<S>> compensator) {
            if (currentStepBuilder == null) {
                throw new IllegalStateException("Must call step() before compensator()");
            }
            currentStepBuilder.compensator(compensator);
            return this;
        }

        /**
         * 标记当前步骤为可选（失败时不回滚整个 Saga）。
         */
        public Builder<S> optional(boolean optional) {
            if (currentStepBuilder == null) {
                throw new IllegalStateException("Must call step() before optional()");
            }
            currentStepBuilder.optional(optional);
            return this;
        }

        /**
         * 构建 SagaDefinition。
         */
        public SagaDefinition<S> build() {
            // 完成最后一步
            if (currentStepBuilder != null) {
                steps.add(currentStepBuilder.build());
            }
            return new SagaDefinition<>(this);
        }
    }
}
